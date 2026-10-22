package com.example.erp.common;

import java.util.List;

public record PageResult<T>(long total, List<T> records) {
}