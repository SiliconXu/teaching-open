package org.jeecg.modules.teaching.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.constant.CacheConstant;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.common.controller.BaseController;
import org.jeecg.modules.teaching.model.ScratchBackpackModel;
import org.jeecg.modules.teaching.service.ITeachingScratchBackpackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Decoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.util.List;

@Slf4j
@Api(tags = "Scratch背包接口")
@RestController
@RequestMapping("/teaching/scratch/backpack")
public class TeachingScratchBackpackController extends BaseController {
    @Autowired
    RedisUtil redisUtil;
    @Autowired
    ITeachingScratchBackpackService teachingScratchBackpackService;

    @GetMapping("/getFile/{filename}")
    public void __getFile(@PathVariable String filename, HttpServletRequest req , HttpServletResponse response){
        String file = (String) redisUtil.get(CacheConstant.SCRATCH_BACKPACK_BODY +filename);
        BASE64Decoder decoder = new BASE64Decoder();
        try {
            OutputStream out = response.getOutputStream();
            // Base64解码
            byte[] b = decoder.decodeBuffer(file);
            for (int i = 0; i < b.length; ++i) {
                if (b[i] < 0) {
                    b[i] += 256;
                }
            }
            out.write(b);
            out.flush();
            out.close();
        } catch (Exception e) {
        }
    }

    /**
     * 背包列表
     * @param limit
     * @param offset
     * @return
     */
    @GetMapping("/content")
    public Result<?> __getBackpack(
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset){

        LoginUser loginUser = getCurrentUser();
        List<ScratchBackpackModel> list = teachingScratchBackpackService.getBackpackList(loginUser.getId(), offset, limit);
        return Result.ok(list);
    }

    /**
     * 保存到背包
     * @param json
     * @return
     */
    @PostMapping("/save")
    public Result<?> __saveBackpack(@RequestBody JSONObject json){
        LoginUser loginUser = getCurrentUser();
        ScratchBackpackModel scratchBackpackModel = teachingScratchBackpackService.saveBackpack(loginUser.getId(), json);
        return Result.ok(scratchBackpackModel);
    }


    /**
     * 删除背包项目
     * @param id
     * @return
     */
    @DeleteMapping("/delete")
    public Result<?> __getBackpack(@RequestParam String id){
        LoginUser loginUser = getCurrentUser();
        teachingScratchBackpackService.deleteBackpack(loginUser.getId(), id);
        return Result.ok();
    }

}
