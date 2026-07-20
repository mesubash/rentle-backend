package com.rentle.domain.template.model;

public enum FieldType {
    TEXT,
    NUMBER,
    DATE,
    SELECT,          // single choice from options
    MULTISELECT,     // several choices from options
    BOOLEAN,
    DOCUMENT,        // single uploaded file (VERIFICATION scope only)
    DOCUMENT_LIST    // several uploaded files (VERIFICATION scope only)
}
