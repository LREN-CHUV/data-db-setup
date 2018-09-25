SET datestyle to 'European';

CREATE TABLE "SAMPLE_DATA"
(
  "id" int,
  "stress_before_test1" numeric,
  "score_test1" numeric,
  "iq" numeric,
  "cognitive_task2" numeric,
  "practice_task2" numeric,
  "response_time_task2" numeric,
  "college_math" numeric,
  "score_math_course1" numeric,
  "score_math_course2" numeric,
  "dataset" varchar(20),

  CONSTRAINT pk_sample_data PRIMARY KEY (id)
)
WITH (
  OIDS=FALSE
);
