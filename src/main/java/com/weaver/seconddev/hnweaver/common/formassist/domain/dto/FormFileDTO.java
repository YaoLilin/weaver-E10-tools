package com.weaver.seconddev.hnweaver.common.formassist.domain.dto;

import lombok.Data;

/**
 * @author 姚礼林
 * @desc 附件、图片字段的文件信息，白名单投影自平台 FileDetail（原对象含 fileContent 字节内容）
 * @date 2026/9/18
 **/
@Data
public class FormFileDTO {

    /**
     * 文件 ID
     */
    private Long fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件访问地址
     */
    private String fileUrl;
}
