package com.mchange.v2.c3p0.cfg;

import java.util.List;

import com.mchange.v2.cfg.DelayedLogItem;
import com.mchange.v2.cfg.PropertiesConfigSource;
import com.mchange.v2.cfg.ExtraPropertiesConfigSources;

public class C3P0ExtraPropertiesConfigSources extends ExtraPropertiesConfigSources
{
    @Override
    public PropertiesConfigSource propertiesConfigSourceForIdentifier(String identifier, List<DelayedLogItem> delayedLogItemsOut)
    {
        if (identifier != null && identifier.startsWith("c3p0-xml:"))
            return new C3P0XmlPropertiesConfigSource();
        else
            return null;
    }
}
