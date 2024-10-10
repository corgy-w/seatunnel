package org.apache.seatunnel.api.table.event.handler;

import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.event.SchemaChangeEvent;

public interface TableSchemaChangeEventHandler extends SchemaChangeEventHandler<TableSchema> {

    TableSchema get();

    TableSchemaChangeEventHandler reset(TableSchema schema);

    default TableSchema handle(SchemaChangeEvent event) {
        if (get() == null) {
            throw new IllegalStateException("Handler not reset");
        }

        try {
            return apply(event);
        } finally {
            reset(null);
            if (get() != null) {
                throw new IllegalStateException("Handler not reset");
            }
        }
    }

    TableSchema apply(SchemaChangeEvent event);
}
