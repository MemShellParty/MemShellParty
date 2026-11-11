package com.reajason.javaweb.boot.vo;

/**
 * 服务端生成元数据。{@code minimumTargetJreVersion} 是 class 文件版本号（52 = Java 8），
 * 前端用它把目标 JDK 抬到该服务端模板能写出的最低版本。
 *
 * @author ReaJason
 * @since 3.2.0
 */
public record ServerVO(String name, int minimumTargetJreVersion) {
}
