
CREATE OR REPLACE VIEW {{ view.name }}
  AS SELECT {{ table1.columns }} FROM {{ table1.name }}
UNION
  SELECT {{ table2.columns }} FROM {{ table2.name }};
