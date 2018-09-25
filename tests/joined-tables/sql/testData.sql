BEGIN;

-- Plan the tests
SELECT plan( 6 );

SELECT is(count(*)::INT, 1, 'Sample #150 should be present')
  FROM "SAMPLE_DATA" where id=150;

SELECT is(count(*)::INT, 150, 'Missing rows?')
  FROM "SAMPLE_DATA";

SELECT is(count(*)::INT, 1, 'Sample #150 should be present')
  FROM "DATA_TO_JOIN" where id=150;

SELECT is(count(*)::INT, 150, 'Missing rows?')
  FROM "DATA_TO_JOIN";

SELECT is(count(*)::INT, 1, 'Migration of testA and testB datasets should be present')
  FROM schema_version where description='Setup datasets testA,testB';

SELECT is(count(*)::INT, 1, 'Creation of view sample_join should be present')
  FROM schema_version where description='Create view sample_join';

-- Clean up
SELECT * FROM finish();
ROLLBACK;
