# First schema checkpoint

This is the literal V1 migration from ticket28 checkpoint `28062c1`, which first passed signed HTTP create/read and application restart on PostgreSQL18.6. It is retained independently from the current migration directory so upgrade tests cannot silently regenerate their predecessor from the implementation under test.

This new application has no historic production database release. The fixture represents its first schema checkpoint; it does not claim evidence for a nonexistent production installation. Later upgrade tests supply literal independently chosen rows and assert their public representation after migration.
