package com.bc.calvalus.production;


import com.bc.calvalus.commons.ProcessState;
import com.bc.calvalus.commons.ProcessStatus;
import com.bc.calvalus.commons.WorkflowException;
import com.bc.calvalus.inventory.InventoryService;
import com.bc.calvalus.inventory.ProductSet;
import com.bc.calvalus.processing.BundleDescriptor;
import com.bc.calvalus.processing.ProcessingService;
import com.bc.calvalus.production.store.ProductionStore;
import com.bc.calvalus.staging.Staging;
import com.bc.calvalus.staging.StagingService;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A ProductionService implementation that delegates to a Hadoop cluster.
 * To use it, specify the servlet init-parameter 'calvalus.portal.backendService.class'
 * (context.xml or web.xml)
 */
public class ProductionServiceImpl implements ProductionService {

    public static enum Action {
        CANCEL,
        DELETE,
        RESTART,// todo - implement restart (nf)
    }

    private final InventoryService inventoryService;
    private final ProcessingService processingService;
    private final StagingService stagingService;
    private final ProductionType[] productionTypes;
    private final ProductionStore productionStore;
    private final Map<String, Action> productionActionMap;
    private final Map<String, Staging> productionStagingsMap;
    private final Logger logger;

    public ProductionServiceImpl(InventoryService inventoryService,
                                 ProcessingService processingService,
                                 StagingService stagingService,
                                 ProductionStore productionStore,
                                 ProductionType... productionTypes) throws ProductionException {
        this.inventoryService = inventoryService;
        this.productionStore = productionStore;
        this.processingService = processingService;
        this.stagingService = stagingService;
        this.productionTypes = productionTypes;
        this.productionActionMap = new HashMap<String, Action>();
        this.productionStagingsMap = new HashMap<String, Staging>();
        this.logger = Logger.getLogger("com.bc.calvalus");
    }

    @Override
    public ProductSet[] getProductSets(String filter) throws ProductionException {
        try {
            return inventoryService.getProductSets(filter);
        } catch (Exception e) {
            throw new ProductionException(e);
        }
    }

    @Override
    public BundleDescriptor[] getBundles(String filter) throws ProductionException {
        try {
            return processingService.getBundles(filter);
        } catch (Exception e) {
            throw new ProductionException("Failed to load list of processors.", e);
        }
    }

    @Override
    public synchronized Production[] getProductions(String filter) throws ProductionException {
        return productionStore.getProductions();
    }

    @Override
    public Production getProduction(String id) throws ProductionException {
        return productionStore.getProduction(id);
    }

    @Override
    public ProductionResponse orderProduction(ProductionRequest productionRequest) throws ProductionException {
        logger.info("orderProduction:");
        logger.info("user: " + productionRequest.getUserName());
        logger.info("type: " + productionRequest.getProductionType());
        logger.info("parameters: " + productionRequest.getParameters());

        ProductionType productionType = findProductionType(productionRequest);
        synchronized (this) {
            Production production = productionType.createProduction(productionRequest);
            try {
                production.getWorkflow().submit();
            } catch (WorkflowException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                throw new ProductionException(String.format("Failed to submit production '%s': %s", production.getId(), e.getMessage()), e);
            }
            productionStore.addProduction(production);
            return new ProductionResponse(production);
        }
    }

    @Override
    public synchronized void stageProductions(String... productionIds) throws ProductionException {
        int count = 0;
        for (String productionId : productionIds) {
            Production production = productionStore.getProduction(productionId);
            if (production != null) {
                try {
                    stageProductionResults(production);
                    count++;
                } catch (ProductionException e) {
                    logger.log(Level.SEVERE, String.format("Failed to stage production '%s': %s",
                                                           production.getId(), e.getMessage()), e);
                }
            }
        }
        if (count < productionIds.length) {
            throw new ProductionException(String.format("Only %d of %d production(s) have been staged.", count, productionIds.length));
        }
    }

    @Override
    public void cancelProductions(String... productionIds) throws ProductionException {
        requestProductionKill(productionIds, Action.CANCEL);
    }

    @Override
    public void deleteProductions(String... productionIds) throws ProductionException {
        requestProductionKill(productionIds, Action.DELETE);
    }

    private void requestProductionKill(String[] productionIds, Action action) throws ProductionException {
        int count = 0;
        for (String productionId : productionIds) {
            Production production = productionStore.getProduction(productionId);
            if (production != null) {
                productionActionMap.put(production.getId(), action);

                Staging staging = productionStagingsMap.get(production.getId());
                if (staging != null && !staging.isCancelled()) {
                    productionStagingsMap.remove(production.getId());
                    staging.cancel();
                }

                if (production.getProcessingStatus().isDone()) {
                    if (action == Action.DELETE) {
                        removeProduction(production);
                    }
                } else {
                    try {
                        production.getWorkflow().kill();
                    } catch (WorkflowException e) {
                        logger.log(Level.SEVERE, String.format("Failed to kill production '%s': %s",
                                                               production.getId(), e.getMessage()), e);
                    }
                }

                count++;
            } else {
                logger.warning(String.format("Failed to kill unknown production '%s'", productionId));
            }
        }
        if (count < productionIds.length) {
            throw new ProductionException(String.format("Only %d of %d production(s) have been killed. See server log for details.",
                                                        count, productionIds.length));
        }
    }

    private void stageProductionResults(Production production) throws ProductionException {
        production.setStagingStatus(ProcessStatus.SCHEDULED);
        ProductionType productionType = findProductionType(production.getProductionRequest());
        Staging staging = productionType.createStaging(production);
        productionStagingsMap.put(production.getId(), staging);
    }

    @Override
    public void updateStatuses() {
        try {
            processingService.updateStatuses();
        } catch (Exception e) {
            logger.warning("Failed to update job statuses: " + e.getMessage());
        }

        // Update state of all registered productions
        Production[] productions = productionStore.getProductions();
        for (Production production : productions) {
            production.getWorkflow().updateStatus();
        }

        // Now try to delete productions
        for (Production production : productions) {
            if (production.getProcessingStatus().isDone()) {
                Action action = productionActionMap.get(production.getId());
                if (action == Action.DELETE) {
                    try {
                        removeProduction(production);
                    } catch (ProductionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Copy result to staging area
        for (Production production : productions) {
            if (production.isAutoStaging()
                    && production.getProcessingStatus().getState() == ProcessState.COMPLETED
                    && production.getStagingStatus().getState() == ProcessState.UNKNOWN
                    && productionStagingsMap.get(production.getId()) == null) {
                try {
                    stageProductionResults(production);
                } catch (ProductionException e) {
                    logger.warning("Failed to stage production: " + e.getMessage());
                }
            }
        }

        // write changes to persistent storage
        try {
            productionStore.persist();
            // logger.info("Production store persisted.");
        } catch (ProductionException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    @Override
    public void close() throws ProductionException {
        try {
            try {
                stagingService.close();
            } finally {
                try {
                    processingService.close();
                } finally {
                    productionStore.close();
                }
            }
        } catch (Exception e) {
            throw new ProductionException("Failed to close production service: " + e.getMessage(), e);
        }
    }

    @Override
    public String[] listUserFiles(String userName, String dirPath) throws ProductionException {
        try {
            String glob = getUserGlob(userName, dirPath);
            return inventoryService.globPaths(Arrays.asList(glob));
        } catch (IOException e) {
            throw new ProductionException(e);
        }
    }

    @Override
    public OutputStream addUserFile(String userName, String path) throws ProductionException {
        try {
            return inventoryService.addFile(getUserPath(userName, path));
        } catch (IOException e) {
            throw new ProductionException(e);
        }
    }

    @Override
    public boolean removeUserFile(String userName, String path) throws ProductionException {
        try {
            return inventoryService.removeFile(getUserPath(userName, path));
        } catch (IOException e) {
            throw new ProductionException(e);
        }
    }

    private String getUserGlob(String userName, String dirPath) {
        return getUserPath(userName, dirPath) + "/.*";
    }
    private String getUserPath(String userName, String dirPath) {
        String path;
        if (dirPath.isEmpty() || "/".equals(dirPath)) {
            path = String.format("home/%s", userName.toLowerCase());
        } else {
            path = String.format("home/%s/%s", userName.toLowerCase(), dirPath);
        }
        return path;
    }

    private ProductionType findProductionType(ProductionRequest productionRequest) throws ProductionException {
        for (ProductionType productionType : productionTypes) {
            if (productionType.getName().equals(productionRequest.getProductionType())) {
                return productionType;
            }
        }
        for (ProductionType productionType : productionTypes) {
            if (productionType.accepts(productionRequest)) {
                return productionType;
            }
        }
        throw new ProductionException(String.format("Unhandled production request of type '%s'",
                                                    productionRequest.getProductionType()));
    }

    private synchronized void removeProduction(Production production) throws ProductionException {
        productionStore.removeProduction(production.getId());
        productionActionMap.remove(production.getId());
        productionStagingsMap.remove(production.getId());
        try {
            stagingService.deleteTree(production.getStagingPath());
        } catch (IOException e) {
            logger.log(Level.SEVERE, String.format("Failed to delete staging directory '%s' of production '%s': %s",
                                                   production.getStagingPath(), production.getId(), e.getMessage()), e);
        }
    }


}
