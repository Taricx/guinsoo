-- Copyright 2004-2021 H2 Group. Multiple-Licensed under the MPL 2.0,
-- and the EPL 1.0 (https://h2database.com/html/license.html).
-- Initial Developer: H2 Group
--

CREATE TABLE TEST(G GEOMETRY);
> ok

CREATE UNIQUE SPATIAL INDEX IDX ON TEST(G);
> exception SYNTAX_ERROR_2

CREATE HASH SPATIAL INDEX IDX ON TEST(G);
> exception SYNTAX_ERROR_2

CREATE UNIQUE HASH SPATIAL INDEX IDX ON TEST(G);
> exception SYNTAX_ERROR_2

DROP TABLE TEST;
> ok
