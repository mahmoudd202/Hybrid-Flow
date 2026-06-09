package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CsvUploadResponse {
    private boolean success;
    private String message;
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int savedRows;
    private List<CsvRowDto> data;

}
