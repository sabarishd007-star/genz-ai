ALTER TABLE documents 
ADD COLUMN IF NOT EXISTS subject VARCHAR(100) DEFAULT 'General';

CREATE INDEX IF NOT EXISTS idx_documents_user_subject 
ON documents(user_id, subject);
