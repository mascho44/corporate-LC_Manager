CREATE TABLE lc_note (
    id UUID PRIMARY KEY,
    letter_of_credit_id UUID NOT NULL REFERENCES letter_of_credit(id) ON DELETE CASCADE,
    username VARCHAR(100) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_lc_note_lc_created ON lc_note (letter_of_credit_id, created_at DESC);
