package com.example.nested;

import java.util.List;

public record OuterRecord(String id, InnerRecord inner, List<DeeplyNestedRecord> tags) {
}
