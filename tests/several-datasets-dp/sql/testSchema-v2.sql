BEGIN;

-- Plan the tests
SELECT plan( 12 );

SELECT has_table( 'sample_data' );

SELECT has_column( 'sample_data', 'id' );
SELECT has_column( 'sample_data', 'stress_before_test1' );
SELECT has_column( 'sample_data', 'cognitive_task2' );
SELECT has_column( 'sample_data', 'practice_task2' );
SELECT has_column( 'sample_data', 'response_time_task2' );
SELECT has_column( 'sample_data', 'college_math' );
SELECT has_column( 'sample_data', 'score_math_course1' );
SELECT has_column( 'sample_data', 'score_math_course2' );
SELECT has_column( 'sample_data', 'dataset' );
SELECT has_column( 'sample_data', 'new_col' );
SELECT col_is_pk(  'sample_data', 'id' );

-- Clean up
SELECT * FROM finish();
ROLLBACK;
