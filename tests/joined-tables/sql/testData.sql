BEGIN;

-- Plan the tests
SELECT plan( 6 );

SELECT is(count(*)::INT, 1, 'Sample #150 should be present')
  FROM sample_data where id=150;

SELECT is(count(*)::INT, 150, 'Missing rows?')
  FROM sample_data;

SELECT is(count(*)::INT, 1, 'Sample #150 should be present')
  FROM data_to_join where id=150;

SELECT is(count(*)::INT, 150, 'Missing rows?')
  FROM data_to_join;

SELECT is(count(*)::INT, 1, 'Migration of testA and testB datasets should be present')
  FROM schema_version where description='Setup datasets testA,testB';

SELECT is(count(*)::INT, 1, 'Creation of view sample_join should be present')
  FROM schema_version where description='Create view sample_join';

-- Clean up
SELECT * FROM finish();
ROLLBACK;
