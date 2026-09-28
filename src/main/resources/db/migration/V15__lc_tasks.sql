CREATE TABLE lc_task (
    id UUID PRIMARY KEY,
    letter_of_credit_id UUID NOT NULL REFERENCES letter_of_credit(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    assigned_to VARCHAR(100),
    due_date DATE,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX idx_lc_task_open_due ON lc_task (completed, due_date);
CREATE INDEX idx_lc_task_lc ON lc_task (letter_of_credit_id, created_at DESC);
