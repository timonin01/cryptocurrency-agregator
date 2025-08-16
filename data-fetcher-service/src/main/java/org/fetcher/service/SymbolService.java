package org.fetcher.service;

import java.util.List;

public interface SymbolService {

    public List<String> fetchSymbols();

    public List<String> getAvailableSymbols();
}
