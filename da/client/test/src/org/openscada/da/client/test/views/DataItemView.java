package org.openscada.da.client.test.views;


import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import org.apache.log4j.Logger;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.part.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.*;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.SWT;
import org.openscada.da.client.net.DataItem;
import org.openscada.da.client.net.ItemUpdateListener;
import org.openscada.da.client.test.impl.HiveItem;
import org.openscada.da.core.data.Variant;


/**
 * This sample class demonstrates how to plug-in a new
 * workbench view. The view shows data obtained from the
 * model. The sample creates a dummy model on the fly,
 * but a real implementation would connect to the model
 * available either in this or another plug-in (e.g. the workspace).
 * The view is connected to the model using a content provider.
 * <p>
 * The view uses a label provider to define how model
 * objects should be presented in the view. Each
 * view can present the same model objects using
 * different labels and icons, if needed. Alternatively,
 * a single label provider can be shared between views
 * in order to ensure that objects of the same type are
 * presented in the same way everywhere.
 * <p>
 */

public class DataItemView extends ViewPart implements ItemUpdateListener
{
    private static Logger _log = Logger.getLogger ( DataItemView.class );
    
    private HiveItem _hiveItem = null;
    
	private TableViewer viewer;
	private Action action1;
	private Action action2;
	private Action doubleClickAction;
    
    private Label _valueLabel;
    private StyledText _console;

	/*
	 * The content provider class is responsible for
	 * providing objects to the view. It can wrap
	 * existing objects in adapters or simply return
	 * objects as-is. These objects may be sensitive
	 * to the current input of the view, or ignore
	 * it and always show the same content 
	 * (like Task List, for example).
	 */
	
    class Entry
    {
        public String name;
        public Variant value;
        
        public Entry ( String name, Variant value )
        {
            this.name = name;
            this.value = value;
        }
    }
    
	class ViewContentProvider implements IStructuredContentProvider, Observer
    {
        private Viewer _viewer = null;
        private DataItem _item = null;
        
		public void inputChanged(Viewer v, Object oldInput, Object newInput)
        {
            _viewer = viewer;
            
            clearItem();
            
            if ( newInput instanceof HiveItem )
            {
                if ( newInput != null ) 
                {
                    HiveItem hiveItem = (HiveItem)newInput;
                    
                    _item = new DataItem ( hiveItem.getItemName() );
                    _item.addObserver ( this );
                    _item.register ( hiveItem.getConnection().getConnection() );
                }
            }
        }
        
        private void clearItem ()
        {
            if ( _item != null )
            {
                _item.deleteObserver(this);
                _item.unregister();
                _item = null;
            }
        }
        
		public void dispose()
        {
            clearItem();
		}
        
		public Object[] getElements(Object parent)
        {
            if ( _item == null )
                return new Object[0];
            
            Map<String,Variant> attrs = _item.getAttributes();
            Entry [] entries = new Entry[attrs.size()];
            int i = 0;
            
            for ( Map.Entry<String,Variant> entry : attrs.entrySet() )
            {
                entries[i++] = new Entry ( entry.getKey(), entry.getValue() );
            }
            return entries;
		}

        public void update ( Observable o, Object arg )
        {
            if ( !_viewer.getControl().isDisposed() )
            {
                _viewer.getControl().getDisplay().asyncExec(new Runnable(){
                    
                    public void run ()
                    {
                        if ( !_viewer.getControl().isDisposed() )
                        {
                            _viewer.refresh();
                        }
                    }});
            }
        }
    }
	class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		public String getColumnText(Object obj, int index)
        {
            if ( !(obj instanceof Entry) )
                return "";
            
            Entry entry = (Entry)obj;
            
            switch ( index )
            {
            case 0:
                return entry.name;
            case 1:            
                return entry.value.asString("<null>");
            }
            return getText(obj);
        }
		public Image getColumnImage(Object obj, int index)
        {
            if ( index == 0 )
                return getImage(obj);
            else
                return null;
		}
		public Image getImage(Object obj) {
			return PlatformUI.getWorkbench().
					getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
		}
	}
	class NameSorter extends ViewerSorter {
	}

	/**
	 * The constructor.
	 */
	public DataItemView()
    {
        
	}

	/**
	 * This is a callback that will allow us
	 * to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent)
    {
        parent.setLayout(new GridLayout(1,false));
        
        GridData gd;
        
        // value label
        gd = new GridData();
        gd.grabExcessHorizontalSpace = true;
        gd.horizontalAlignment = SWT.FILL;
        gd.verticalAlignment = SWT.CENTER;
        _valueLabel = new Label ( parent, SWT.NONE );
        _valueLabel.setLayoutData ( gd );
        
        SashForm box = new SashForm ( parent, SWT.VERTICAL );        
        gd = new GridData();
        gd.grabExcessHorizontalSpace = true;
        gd.grabExcessVerticalSpace = true;
        gd.horizontalAlignment = SWT.FILL;
        gd.verticalAlignment = SWT.FILL;
        box.setLayoutData ( gd );
        
        // attributes table
        
		viewer = new TableViewer(box, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        viewer.getControl().setLayoutData(gd);
		viewer.setContentProvider(new ViewContentProvider());
		viewer.setLabelProvider(new ViewLabelProvider());
        
        TableColumn col;
        
        col = new TableColumn(viewer.getTable(),SWT.NONE);
        col.setText ( "Name" );
        col.setWidth(200);
        col = new TableColumn(viewer.getTable(),SWT.NONE);
        col.setText ( "Value" );
        col.setWidth(500);
		
        viewer.getTable().setHeaderVisible(true);
        viewer.setSorter(new NameSorter());
        
        // console window
        _console = new StyledText ( box, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY );
        _console.setLayoutData ( gd );
        
        // set up sash
        box.setWeights ( new int[]{60,40} );
        //box.setMaximizedControl ( _console );
        
        // actions
		makeActions();
		//hookContextMenu();
		//hookDoubleClickAction();
		//contributeToActionBars();
	}
    
    private void appendConsoleMessage ( final String message )
    {
        if (!_console.isDisposed() )
        {
            _console.getDisplay().asyncExec(new Runnable(){
                
                public void run ()
                {
                    if ( !_console.isDisposed() )
                    {
                        _console.append ( message + "\n" );
                        _console.setSelection(_console.getCharCount());
                        _console.showSelection();
                    }
                }});
        }
    }
    
    private void setValue ( final Variant variant )
    {
        if ( !_valueLabel.isDisposed() )
        {
            _valueLabel.getDisplay().asyncExec(new Runnable(){

                public void run ()
                {
                    if ( !_valueLabel.isDisposed() )
                    {
                        if ( variant.isNull() )
                        {
                            _valueLabel.setText("Value: <null>");
                        }
                        else
                        {
                            _valueLabel.setText("Value: " + variant.asString("BUG!"));
                        }
                    }
                }});
        }
    }

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				DataItemView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(action1);
		manager.add(new Separator());
		manager.add(action2);
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(action1);
		manager.add(action2);
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}
	
	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(action1);
		manager.add(action2);
	}

	private void makeActions() {
		action1 = new Action() {
			public void run() {
				showMessage("Action 1 executed");
			}
		};
		action1.setText("Action 1");
		action1.setToolTipText("Action 1 tooltip");
		action1.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
			getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		
		action2 = new Action() {
			public void run() {
				showMessage("Action 2 executed");
			}
		};
		action2.setText("Action 2");
		action2.setToolTipText("Action 2 tooltip");
		action2.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		doubleClickAction = new Action() {
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection)selection).getFirstElement();
				showMessage("Double-click detected on "+obj.toString());
			}
		};
	}

	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
	}
	private void showMessage(String message) {
		MessageDialog.openInformation(
			viewer.getControl().getShell(),
			"Data Item View",
			message);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}

    @Override
    public void dispose ()
    {
        setDataItem ( null );
        super.dispose ();
    }
    
	public void setDataItem ( HiveItem item )
    {
        if ( _hiveItem != null )
        {
            _hiveItem.getConnection().getConnection().removeItemUpdateListener(_hiveItem.getItemName(),this);
            appendConsoleMessage("Unsubscribe from item: " + _hiveItem.getItemName() );
        }
        
        if ( item != null )
        {
            _log.info ( "Set data item: " + item.getItemName() );
            
            _hiveItem = item;
            
            appendConsoleMessage("Subscribe to item: " + _hiveItem.getItemName() );
            _hiveItem.getConnection().getConnection().addItemUpdateListener(_hiveItem.getItemName(),true,this);
            
            viewer.setInput ( item );
        }
    }

    public void notifyValueChange ( Variant value, boolean initial )
    {
        appendConsoleMessage("Value change event: " + value.asString("<null>") + " " + ( initial ? "initial" : "" ));
        setValue(value);
    }

    public void notifyAttributeChange ( Map<String, Variant> attributes, boolean initial )
    {
        appendConsoleMessage("Attribute change set " + (initial?"(initial)":"") + " " + attributes.size() + " item(s) follow:");
        int i = 0;
        for ( Map.Entry<String,Variant> entry : attributes.entrySet() )
        {
            String q = entry.getValue().isNull() ? "" : "'";
            appendConsoleMessage ( "#" + i + ": " + entry.getKey() + "->" + q + entry.getValue().asString("<null>") + q );
        }
    }
}