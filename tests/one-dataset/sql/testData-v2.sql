BEGIN;

-- Plan the tests
SELECT plan( 3 );

SELECT is(count(*)::INT, 1, 'Sample #149 should be present')
  FROM sample_data where id=149;

SELECT is(count(*)::INT, 149, 'Old records remaining?')
  FROM sample_data;

SELECT is(count(*)::INT, 2, 'Migration and upgrade of test1 dataset should be present')
  FROM schema_version where description='Setup dataset test1';

-- Clean up
SELECT * FROM finish();
ROLLBACK;
