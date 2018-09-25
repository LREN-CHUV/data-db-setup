BEGIN;

-- Plan the tests
SELECT plan( 5 );

SELECT is(count(*)::INT, 1, 'Sample #99 should be present')
  FROM "SAMPLE_DATA" where id=99 and dataset='testA';

SELECT is(count(*)::INT, 99, 'Missing rows in SAMPLE_DATA?')
  FROM "SAMPLE_DATA";

SELECT is(count(*)::INT, 1, 'Sample #150 should be present')
  FROM "SIMILAR_DATA" where id=150 and dataset='testB';

SELECT is(count(*)::INT, 51, 'Missing rows in SIMILAR_DATA?')
  FROM "SIMILAR_DATA";

SELECT is(count(*)::INT, 1, 'Migration of testA and testB datasets should be present')
  FROM schema_version where description='Setup datasets testA,testB';

-- Clean up
SELECT * FROM finish();
ROLLBACK;
