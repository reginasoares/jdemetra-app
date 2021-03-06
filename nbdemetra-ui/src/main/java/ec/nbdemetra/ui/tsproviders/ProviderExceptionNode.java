/*
 * Copyright 2013 National Bank of Belgium
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
package ec.nbdemetra.ui.tsproviders;

import ec.nbdemetra.ui.nodes.ExceptionNode;
import ec.tss.tsproviders.IDataSourceProvider;
import ec.tss.tsproviders.TsProviders;
import java.awt.Image;
import java.io.IOException;
import org.openide.nodes.Sheet;

/**
 *
 * @author Philippe Charles
 */
final class ProviderExceptionNode extends ExceptionNode {

    private final String providerName;

    public ProviderExceptionNode(IOException ex, String providerName) {
        super(ex);
        this.providerName = providerName;
    }

    @Override
    public String getHtmlDisplayName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        IOException ex = getLookup().lookup(IOException.class);
        return TsProviders.lookup(IDataSourceProvider.class, providerName).get().getDisplayName(ex);
    }

    @Override
    public Image getIcon(int type) {
        IOException ex = getLookup().lookup(IOException.class);
        return DataSourceProviderBuddySupport.getDefault().get(providerName).getIcon(ex, type, false);
    }

    @Override
    public Image getOpenedIcon(int type) {
        IOException ex = getLookup().lookup(IOException.class);
        return DataSourceProviderBuddySupport.getDefault().get(providerName).getIcon(ex, type, true);
    }

    @Override
    protected Sheet createSheet() {
        IOException ex = getLookup().lookup(IOException.class);
        return DataSourceProviderBuddySupport.getDefault().get(providerName).createSheet(ex);
    }
}
