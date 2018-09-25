BEGIN;

-- Plan the tests
SELECT plan( 21 );

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

SELECT has_table( 'SIMILAR_DATA' );

SELECT has_column( 'SIMILAR_DATA', 'id' );
SELECT has_column( 'SIMILAR_DATA', 'stress_before_test1' );
SELECT has_column( 'SIMILAR_DATA', 'cognitive_task2' );
SELECT has_column( 'SIMILAR_DATA', 'practice_task2' );
SELECT has_column( 'SIMILAR_DATA', 'response_time_task2' );
SELECT has_column( 'SIMILAR_DATA', 'college_math' );
SELECT has_column( 'SIMILAR_DATA', 'score_math_course1' );
SELECT has_column( 'SIMILAR_DATA', 'score_math_course2' );
SELECT col_is_pk(  'SIMILAR_DATA', 'id' );

SELECT views_are(ARRAY[ 'SAMPLE_UNION' ]);

-- Clean up
SELECT * FROM finish();
ROLLBACK;
