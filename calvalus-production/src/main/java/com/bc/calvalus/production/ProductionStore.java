package com.bc.calvalus.production;

import java.io.IOException;

/**
 * Persistence for productions.
 *
 * @author Norman
 */
public interface ProductionStore {
    void addProduction(Production production);

    void removeProduction(Production production);

    Production[] getProductions();

    Production getProduction(String productionId);

    void load() throws IOException;

    void store() throws IOException;

    /**
     * Indicates the service will no longer be used.
     * Invocation has no additional effect if already closed.
     */
    void close() throws IOException;
}
