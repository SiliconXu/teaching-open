create table teaching_scratch_backpack
(
    id varchar(40) not null
        primary key,
    user_id varchar(40) not null comment '所属用户id',
    name varchar(255) null comment '名称',
    mime varchar(100) null comment 'mime',
    type varchar(50) null comment '条目类型',
    body_path varchar(1000) null comment '内容存储路径',
    thumbnail_path varchar(1000) null comment '缩略图存储路径',
    storage_type varchar(20) not null comment '存储类型 local/minio/qiniu/alioss',
    create_time datetime null comment '创建时间',
    update_time datetime null comment '更新时间',
    del_flag tinyint default 0 not null comment '删除状态'
);

create index userCreateTimeIndex
    on teaching_scratch_backpack (user_id, create_time);

