BEGIN;

-- Plan the tests
SELECT plan( 15 );

SELECT has_table( 'SAMPLE_DATA' );

SELECT has_column( 'SAMPLE_DATA', 'id' );
SELECT has_column( 'SAMPLE_DATA', 'stress_before_test1' );
SELECT has_column( 'SAMPLE_DATA', 'cognitive_task2' );
SELECT has_column( 'SAMPLE_DATA', 'practice_task2' );
SELECT has_column( 'SAMPLE_DATA', 'response_time_task2' );
SELECT has_column( 'SAMPLE_DATA', 'college_math' );
SELECT has_column( 'SAMPLE_DATA', 'score_math_course1' );
SELECT has_column( 'SAMPLE_DATA', 'score_math_course2' );
SELECT col_is_pk(  'SAMPLE_DATA', 'id' );

SELECT has_table( 'DATA_TO_JOIN' );

SELECT has_column( 'DATA_TO_JOIN', 'id' );
SELECT has_column( 'DATA_TO_JOIN', 'dataset' );
SELECT col_is_pk(  'DATA_TO_JOIN', 'id' );

SELECT views_are(ARRAY[ 'SAMPLE_JOIN' ]);

-- Clean up
SELECT * FROM finish();
ROLLBACK;
