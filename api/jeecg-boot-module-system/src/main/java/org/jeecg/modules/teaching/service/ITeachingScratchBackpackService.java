package org.jeecg.modules.teaching.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.teaching.entity.TeachingScratchBackpack;
import org.jeecg.modules.teaching.model.ScratchBackpackModel;

import java.util.List;

public interface ITeachingScratchBackpackService extends IService<TeachingScratchBackpack> {
    List<ScratchBackpackModel> getBackpackList(String userId, Integer offset, Integer limit);

    ScratchBackpackModel saveBackpack(String userId, JSONObject json);

    void deleteBackpack(String userId, String id);
}
