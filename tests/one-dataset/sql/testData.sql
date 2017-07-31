BEGIN;

-- Plan the tests
SELECT plan( 1 );

SELECT is(count(*)::INT, 1, 'Sample #150 should be present')
  FROM sample_data where id=150;

-- Clean up
SELECT * FROM finish();
ROLLBACK;
