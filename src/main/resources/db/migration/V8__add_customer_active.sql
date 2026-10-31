alter table customers add column active boolean not null default true;
-- document_number is already unique and indexed; no redundant status index is needed.
