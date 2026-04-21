package com.hpe.cap_rotation_balance.domain.repository;

import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelOrderDTO;
import com.hpe.cap_rotation_balance.features.ingestion.dto.ExcelPriceDTO;

import java.io.InputStream;
import java.util.List;

public interface OrderDataSource {
    List<ExcelOrderDTO> fetchRawOrders(InputStream source);
    List<ExcelPriceDTO> fetchPriceReports(InputStream source);
}
