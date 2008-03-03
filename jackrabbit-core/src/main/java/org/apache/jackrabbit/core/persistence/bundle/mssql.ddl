create table ${schemaObjectPrefix}BUNDLE (NODE_ID binary(16) not null, BUNDLE_DATA image not null)
create unique index ${schemaObjectPrefix}BUNDLE_IDX on ${schemaObjectPrefix}BUNDLE (NODE_ID)
create table ${schemaObjectPrefix}REFS (NODE_ID binary(16) not null, REFS_DATA image not null)
create unique index ${schemaObjectPrefix}REFS_IDX on ${schemaObjectPrefix}REFS (NODE_ID)
create table ${schemaObjectPrefix}BINVAL (BINVAL_ID varchar(64) not null, BINVAL_DATA image not null)
create unique index ${schemaObjectPrefix}BINVAL_IDX on ${schemaObjectPrefix}BINVAL (BINVAL_ID)
create table ${schemaObjectPrefix}NAMES (ID INTEGER IDENTITY(1,1) PRIMARY KEY, NAME varchar(255) COLLATE Latin1_General_CS_AS not null)