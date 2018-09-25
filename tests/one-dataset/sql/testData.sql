BEGIN;

-- Plan the tests
SELECT plan( 3 );

SELECT is(count(*)::INT, 1, 'Sample #150 should be present')
  FROM "SAMPLE_DATA" where id=150;

SELECT is(count(*)::INT, 150, 'Missing rows?')
  FROM "SAMPLE_DATA";

SELECT is(count(*)::INT, 1, 'Migration of test1 dataset should be present')
  FROM schema_version where description='Setup dataset test1';

-- Clean up
SELECT * FROM finish();
ROLLBACK;
