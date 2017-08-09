
CREATE OR REPLACE VIEW sample_join ({{ view.columns }})
  AS SELECT {{ table1.prefixedColumns }} FROM {{ table1.name }}
UNION
  SELECT {{ table2.columns }} FROM {{ table2.name }};
