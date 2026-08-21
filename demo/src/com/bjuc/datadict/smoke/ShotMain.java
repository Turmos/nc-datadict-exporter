package com.bjuc.datadict.smoke;

import com.bjuc.datadict.ui.MainFrame;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;

/** 截图辅助：显示主界面并抓屏到 demo_out/shot_{name}.png。 */
public class ShotMain {
    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "ui";
        MainFrame frame = new MainFrame();
        frame.setAlwaysOnTop(true);
        frame.setLocation(60, 40);
        frame.setVisible(true);
        Thread.sleep(1600);
        Rectangle r = new Rectangle(frame.getX(), frame.getY(), frame.getWidth(), frame.getHeight());
        BufferedImage img = new Robot().createScreenCapture(r);
        File out = new File("demo_out/shot_" + name + ".png");
        ImageIO.write(img, "png", out);
        frame.dispose();
        System.exit(0);
    }
}