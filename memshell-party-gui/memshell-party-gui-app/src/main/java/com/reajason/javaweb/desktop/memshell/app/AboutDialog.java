package com.reajason.javaweb.desktop.memshell.app;

import com.reajason.javaweb.desktop.memshell.util.AppVersion;
import com.reajason.javaweb.desktop.memshell.util.ClipboardUtil;
import com.reajason.javaweb.desktop.memshell.util.SwingUiUtil;
import com.reajason.javaweb.memshell.PluginDescriptor;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * 关于弹窗：应用名 / 版本 / 简介 / 作者 / 项目地址 / 许可证。
 * 项目地址点击后尝试打开浏览器，失败则复制到剪贴板兜底；Esc / 关闭按钮仅隐藏，实例可复用。
 */
public class AboutDialog extends JDialog {
    private static final String PROJECT_URL = "https://github.com/MemShellParty/MemShellParty";
    private static final Color FALLBACK_LINK = new Color(0x2A, 0x65, 0xB8);

    public AboutDialog(Window owner) {
        this(owner, Collections.<PluginDescriptor>emptyList());
    }

    public AboutDialog(Window owner, List<PluginDescriptor> plugins) {
        super(owner, "关于 MemShellParty", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);

        JLabel titleLabel = new JLabel("MemShellParty");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        JLabel versionLabel = mutedLabel("版本 v" + AppVersion.get());
        JLabel descLabel = new JLabel("专注于主流 Web 中间件的内存马快速生成工具");
        JLabel authorLabel = new JLabel("作者：ReaJason");
        JLabel linkLabel = createLinkLabel();
        JLabel licenseLabel = mutedLabel("MIT License · Copyright © 2024 ReaJason");

        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> setVisible(false));

        JPanel content = new JPanel(new MigLayout("insets 20 28 14 28, fillx, wrap 1", "[center]", "[]2[]10[]4[]2[]14[]12[]10[]"));
        content.add(titleLabel);
        content.add(versionLabel);
        content.add(descLabel);
        content.add(authorLabel);
        content.add(linkLabel);
        content.add(licenseLabel);
        if (plugins != null && !plugins.isEmpty()) {
            content.add(buildPluginList(plugins), "growx");
        }
        content.add(closeButton, "align right");
        setContentPane(content);

        getRootPane().setDefaultButton(closeButton);
        bindEscape();
        pack();
    }

    /**
     * 只读插件列表：元信息由启动期 {@code PluginBootstrap} 扫描，这里不做任何安装/卸载操作。
     */
    private JComponent buildPluginList(List<PluginDescriptor> plugins) {
        JPanel list = new JPanel(new MigLayout("insets 8, fillx, wrap 1, gapy 6", "[grow,fill]", ""));
        for (int i = 0; i < plugins.size(); i++) {
            if (i > 0) {
                list.add(new JSeparator(), "growx");
            }
            list.add(buildPluginRow(plugins.get(i)), "growx");
        }
        JScrollPane scrollPane = new JScrollPane(list,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createTitledBorder("已安装插件"));
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        int height = Math.min(260, 44 + plugins.size() * 76);
        scrollPane.setPreferredSize(new Dimension(560, height));
        return scrollPane;
    }

    private JPanel buildPluginRow(PluginDescriptor plugin) {
        JPanel row = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]2[]2[]"));
        JLabel title = new JLabel();
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        StringBuilder titleText = new StringBuilder(plugin.getName());
        if (plugin.getVersion() != null) {
            titleText.append("  v").append(plugin.getVersion());
        }
        if (!plugin.isLoaded()) {
            titleText.append("  [加载失败]");
            title.setForeground(SwingUiUtil.errorColor());
        }
        title.setText(titleText.toString());
        title.setToolTipText(plugin.getJarFileName());
        row.add(title);

        String meta = joinPluginMeta(plugin.getAuthor(), plugin.getHomepage());
        if (!meta.isEmpty()) {
            row.add(mutedLabel(meta));
        }
        if (plugin.getDescription() != null) {
            JTextArea description = new JTextArea(plugin.getDescription());
            description.setEditable(false);
            description.setLineWrap(true);
            description.setWrapStyleWord(true);
            description.setOpaque(false);
            description.setBorder(null);
            description.setFocusable(false);
            description.setFont(UIManager.getFont("Label.font"));
            description.setForeground(SwingUiUtil.mutedColor());
            description.setColumns(44);
            description.setRows(2);
            description.setToolTipText(plugin.getDescription());
            row.add(description, "growx");
        }
        if (plugin.getLoadError() != null) {
            JLabel error = mutedLabel(plugin.getLoadError());
            error.setForeground(SwingUiUtil.errorColor());
            row.add(error);
        }
        return row;
    }

    private static String joinPluginMeta(String author, String homepage) {
        StringBuilder meta = new StringBuilder();
        if (author != null) {
            meta.append("作者：").append(author);
        }
        if (homepage != null) {
            if (meta.length() > 0) {
                meta.append("  ·  ");
            }
            meta.append(homepage);
        }
        return meta.toString();
    }

    /**
     * Esc 关闭：仅隐藏不销毁，菜单重复打开时复用同一实例。
     */
    private void bindEscape() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeAboutDialog");
        getRootPane().getActionMap().put("closeAboutDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });
    }

    /**
     * 链接样式标签：前景色走 UIManager/FlatLaf 调色板（updateUI 随主题刷新），点击打开项目主页。
     */
    private JLabel createLinkLabel() {
        JLabel linkLabel = new JLabel(PROJECT_URL) {
            @Override
            public void updateUI() {
                super.updateUI();
                Color color = UIManager.getColor("Component.linkColor");
                setForeground(color != null ? color : FALLBACK_LINK);
            }
        };
        linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        linkLabel.setToolTipText("在浏览器中打开项目主页");
        linkLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openProjectPage();
            }
        });
        return linkLabel;
    }

    private void openProjectPage() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(PROJECT_URL));
                return;
            }
        } catch (Exception ignored) {
            // 落到复制兜底
        }
        if (ClipboardUtil.copyText(PROJECT_URL)) {
            JOptionPane.showMessageDialog(this, "无法打开浏览器，项目地址已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "无法打开浏览器，且系统剪贴板暂不可用：\n" + PROJECT_URL,
                    "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * 次要信息标签：前景色随主题（updateUI 时重读调色板），暗色切换不留亮色残影。
     */
    private static JLabel mutedLabel(String text) {
        return new JLabel(text) {
            @Override
            public void updateUI() {
                super.updateUI();
                setForeground(SwingUiUtil.mutedColor());
            }
        };
    }
}
