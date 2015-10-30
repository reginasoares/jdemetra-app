/*
 * Copyright 2015 National Bank of Belgium
 * 
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved 
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and 
 * limitations under the Licence.
 */
package ec.nbdemetra.spreadsheet;

import ec.nbdemetra.ui.ns.AbstractNamedService;
import ec.nbdemetra.ui.properties.IBeanEditor;
import ec.nbdemetra.ui.properties.NodePropertySetBuilder;
import ec.nbdemetra.ui.properties.OpenIdePropertySheetBeanEditor;
import ec.nbdemetra.ui.tssave.ITsSave;
import ec.tss.Ts;
import ec.tss.TsCollectionInformation;
import ec.tss.TsInformation;
import ec.tss.TsInformationType;
import ec.tss.tsproviders.spreadsheet.engine.SpreadSheetFactory;
import ec.tss.tsproviders.spreadsheet.engine.TsExportOptions;
import ec.util.spreadsheet.Book;
import ec.util.spreadsheet.helpers.ArraySheet;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.filechooser.FileFilter;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileChooserBuilder;
import org.openide.nodes.Sheet;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Philippe Charles
 */
@ServiceProvider(service = ITsSave.class)
public final class SpreadsheetTsSave extends AbstractNamedService implements ITsSave {

    private final FileChooserBuilder fileChooserBuilder;
    private final OptionsEditor optionsEditor;
    private final OptionsBean optionsBean;

    public SpreadsheetTsSave() {
        super(ITsSave.class, "SpreadsheetTsSave");
        this.fileChooserBuilder = new FileChooserBuilder(SpreadsheetTsSave.class)
                .setFileFilter(new SaveFileFilter())
                .setSelectionApprover(new SaveSelectionApprover());
        this.optionsEditor = new OptionsEditor();
        this.optionsBean = new OptionsBean();
    }

    @Override
    public void save(Ts[] ts) {
        File file = fileChooserBuilder.showSaveDialog();
        if (file != null) {
            Book.Factory factory = getFactoryByFile(file);
            if (factory != null) {
                if (optionsEditor.editBean(optionsBean)) {
                    save(ts, file, factory, optionsBean.getTsExportOptions());
                }
            }
        }
    }

    @Override
    public String getDisplayName() {
        return "Spreadsheet file";
    }

    //<editor-fold defaultstate="collapsed" desc="Implementation details">
    private void save(Ts[] data, File file, Book.Factory bookFactory, TsExportOptions options) {
        TsCollectionInformation col = new TsCollectionInformation();
        for (Ts o : data) {
            col.items.add(new TsInformation(o, TsInformationType.All));
        }
        ArraySheet sheet = SpreadSheetFactory.getDefault().fromTsCollectionInfo(col, options);
        try {
            bookFactory.store(file, sheet.toBook());
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Nullable
    private static Book.Factory getFactoryByFile(@Nonnull File file) {
        for (Book.Factory o : Lookup.getDefault().lookupAll(Book.Factory.class)) {
            if (o.canStore() && o.accept(file)) {
                return o;
            }
        }
        return null;
    }

    private static final class SaveFileFilter extends FileFilter {

        @Override
        public boolean accept(File f) {
            return getFactoryByFile(f) != null;
        }

        @Override
        public String getDescription() {
            return "Spreadsheet file";
        }
    }

    private static final class SaveSelectionApprover implements FileChooserBuilder.SelectionApprover {

        @Override
        public boolean approve(File[] selection) {
            if (selection.length > 0 && selection[0].exists()) {
                NotifyDescriptor d = new NotifyDescriptor.Confirmation("Overwrite file?", NotifyDescriptor.OK_CANCEL_OPTION);
                return DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.OK_OPTION;
            }
            return selection.length != 0;
        }
    }

    public static final class OptionsBean {

        public boolean vertical = true;
        public boolean showDates = true;
        public boolean showTitle = true;
        public boolean beginPeriod = true;

        private TsExportOptions getTsExportOptions() {
            return TsExportOptions.create(vertical, showDates, showTitle, beginPeriod);
        }
    }

    private static final class OptionsEditor implements IBeanEditor {

        private Sheet getSheet(OptionsBean bean) {
            Sheet result = new Sheet();
            NodePropertySetBuilder b = new NodePropertySetBuilder();

            b.withBoolean().selectField(bean, "vertical").display("Vertical alignment").add();
            b.withBoolean().selectField(bean, "showDates").display("Include date headers").add();
            b.withBoolean().selectField(bean, "showTitle").display("Include title headers").add();
            b.withBoolean().selectField(bean, "beginPeriod").display("Begin period").add();
            result.put(b.build());

            return result;
        }

        @Override
        final public boolean editBean(Object bean) {
            OptionsBean config = (OptionsBean) bean;
            return OpenIdePropertySheetBeanEditor.editSheet(getSheet(config), "Options", null);
        }
    }
    //</editor-fold>
}
