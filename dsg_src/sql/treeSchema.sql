drop table if exists treeNode;
create table treeNode (
   nodeId BIGINT not null,
   position INTEGER not null,
   player INTEGER not null,
   type INTEGER not null,
   comment VARCHAR(255),
   hash INTEGER not null,
   rotation INTEGER not null,
   depth INTEGER not null,
   parentId BIGINT,
   defaultNextMoveId BIGINT,
   nextMoveIndex INTEGER,
   primary key (nodeId)
);
alter table treeNode add index (defaultNextMoveId), add constraint FK529C96C0BCCA5EC0 foreign key (defaultNextMoveId) references treeNode (nodeId);
alter table treeNode add index (parentId), add constraint FK529C96C0460B8F65 foreign key (parentId) references treeNode (nodeId);
