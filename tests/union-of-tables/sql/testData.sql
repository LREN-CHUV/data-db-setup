BEGIN;

-- Plan the tests
SELECT plan( 5 );

SELECT is(count(*)::INT, 1, 'Sample #99 should be present')
  FROM sample_data where id=99 and dataset='testA';

SELECT is(count(*)::INT, 99, 'Missing rows in sample_data?')
  FROM sample_data;

SELECT is(count(*)::INT, 1, 'Sample #150 should be present')
  FROM similar_data where id=150 and dataset='testB';

SELECT is(count(*)::INT, 51, 'Missing rows in similar_data?')
  FROM similar_data;

SELECT is(count(*)::INT, 1, 'Migration of testA and testB datasets should be present')
  FROM schema_version where description='Setup datasets testA,testB';

-- Clean up
SELECT * FROM finish();
ROLLBACK;
