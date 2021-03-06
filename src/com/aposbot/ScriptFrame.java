package com.aposbot;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.aposbot._default.IClient;
import com.aposbot._default.IScript;
import com.aposbot._default.IScriptListener;

public final class ScriptFrame extends Frame {

	private static final long serialVersionUID = -7439187170235715096L;
	private static final String PROCESSING_ERROR = "Error processing script. Send this output to the script's author:";
    static final File dir = new File("." + File.separator + "Scripts" + File.separator);

    private ScriptEngineManager manager;
    private final java.awt.List displayed_list;
    private final TextField field;
    private final IClient client;

    public ScriptFrame(IClient client) {
        super("Scripts");
        setFont(Constants.UI_FONT);
        
        this.client = client;

        setIconImages(Constants.ICONS);
        addWindowListener(new StandardCloseHandler(this, StandardCloseHandler.HIDE));

        Panel fpanel = new Panel();
        fpanel.setLayout(new GridLayout(1, 0));
        Label l = new Label("Parameters:");
        l.setFont(Constants.UI_FONT);
        fpanel.add(l);
        fpanel.add(field = new TextField(""));
        field.setFont(Constants.UI_FONT);
        add(fpanel, BorderLayout.NORTH);

        displayed_list = new java.awt.List();
        displayed_list.setFont(Constants.UI_FONT);
        final ScrollPane scroll = new ScrollPane();
        scroll.setFont(Constants.UI_FONT);
        scroll.add(displayed_list);
        add(scroll, BorderLayout.CENTER);

        final Panel buttonPanel = new Panel();

        final Button okButton = new Button("OK");
        okButton.setFont(Constants.UI_FONT);
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        initScript();
                    }
                }, "ScriptInit").start();
            }
        });
        buttonPanel.add(okButton);

        final Button cancelButton = new Button("Cancel");
        cancelButton.setFont(Constants.UI_FONT);
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });
        buttonPanel.add(cancelButton);

        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setMinimumSize(getSize());

        final Insets in = getInsets();

        setSize(in.right + in.left + 310, in.top + in.bottom + 240);
    }
    
    private void initScript() {
        final IScriptListener listener = client.getScriptListener();
        if (listener.isScriptRunning()) {
            System.out.println("Stop the script you are running first.");
            return;
        }
        listener.setIScript(null);
        System.gc();

        final String selected_name = displayed_list.getSelectedItem();
        IScript script = null;
        if (selected_name.endsWith(".class")) {
            script = initJavaScript(selected_name);
        } else {
            script = initJSEScript(selected_name);
        }
        if (script == null) return;
        System.out.println("Selected script: " + script);
        try {
            script.init(field.getText());
        } catch (Throwable t) {
            System.out.println(PROCESSING_ERROR);
            t.printStackTrace();
            return;
        }
        field.setText("");
        client.getScriptListener().setIScript(script);
        System.out.println("Press the \"Start script\" button to start it.");
        setVisible(false);
    }
    
    private IScript initJSEScript(String name) {
        if (manager == null) {
            manager = new ScriptEngineManager();
        }
        int dot = name.indexOf('.');
        if (dot == -1) {
            System.out.println("Error: " + name + " has no file extension.");
            return null;
        }
        String ext = name.substring(dot + 1);
        ScriptEngine engine = manager.getEngineByExtension(ext);
        if (engine == null) {
            System.out.println("Error: No script engine found for " + ext);
            return null;
        }
        if (!(engine instanceof Invocable)) {
            System.out.println("Error: Engine for " + ext + " is not invocable.");
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(new File(dir, name))
                    , Constants.UTF_8
                )
            );
            engine.eval(r);
        } catch (Throwable t) {
            System.out.println("Error loading script:");
            t.printStackTrace();
            return null;
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ex) {
                }
            }
        }
        return client.createInvocableScript((Invocable) engine, name);
    }
    
    private IScript initJavaScript(String name) {
        Class<?> c;
        try {
            c = new ScriptClassLoader().loadClass(
                name.substring(0, name.length() - 6)
            );
        } catch (final ClassNotFoundException t) {
            System.out.println("Error loading script:");
            t.printStackTrace();
            return null;
        }

        if (!IScript.class.isAssignableFrom(c)) {
            System.out.println("Error: " + name + " is not a valid Java script.");
            return null;
        }
        
        try {
            return (IScript) c.getConstructor(Class.forName("Extension")).newInstance(client);
        } catch (final Throwable t) {
            System.out.println("Failed to load script " + name + ":");
            t.printStackTrace();
        }
        return null;
    }

    private void update() {
        final String[] files = dir.list();
        if (files == null)
            return;

        final java.util.List<String> list = new ArrayList<>();

        final int len = files.length;
        for (int i = 0; i < len; i++) {
            final String name = files[i];
            if (new File(name).isDirectory()) {
                continue;
            }
            if (name.endsWith(".java") || 
                name.indexOf('$') != -1 || 
                name.startsWith("_") || 
                name.indexOf('.') == -1) {
                continue;
            }
            list.add(name);
        }
        Collections.sort(list);

        displayed_list.removeAll();
        for (final String str : list) {
            displayed_list.add(str);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            update();
            toFront();
            requestFocus();
        }
        super.setVisible(visible);
    }
}
