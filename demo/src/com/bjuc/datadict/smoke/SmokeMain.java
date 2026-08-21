package com.bjuc.datadict.smoke;

import com.bjuc.datadict.ui.MainFrame;

/**
 * 冒烟测试：仅构建界面并立即释放，不显示窗口。
 * 用于验证主程序可正常装载（依赖、模板、UI 装配）。
 */
public class SmokeMain {
    public static void main(String[] args) throws Exception {
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
        } catch (Throwable ignore) {
        }
        long t0 = System.currentTimeMillis();
        MainFrame frame = new MainFrame();
        frame.dispose();
        System.out.println("SMOKE_OK buildMs=" + (System.currentTimeMillis() - t0));
    }
}