BEGIN;

-- Plan the tests
SELECT plan( 4 );

SELECT is(count(*)::INT, 1, 'Sample #99 should be present')
  FROM sample_data where id=99 and dataset='test1';

SELECT is(count(*)::INT, 1, 'Sample #150 should be present')
  FROM sample_data where id=150 and dataset='test2';

SELECT is(count(*)::INT, 149, 'Old records remaining?')
  FROM sample_data;

SELECT is(count(*)::INT, 2, 'Migration and upgrade of test1 and test2 datasets should be present')
  FROM schema_version where description='Setup datasets test1,test2';

-- Clean up
SELECT * FROM finish();
ROLLBACK;
