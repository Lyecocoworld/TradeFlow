package unprotesting.com.github.data;

import java.io.IOException;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import unprotesting.com.github.data.CollectFirst.CollectFirstSetting;

/**
 * Serializer for CollectFirst class.
 */
public class CollectFirstSerializer implements Serializer<CollectFirst> {

    @Override
    public void serialize(DataOutput2 out, CollectFirst value) throws IOException {
        out.writeUTF(value.setting.name());
        out.writeBoolean(value.foundInServer);
    }

    @Override
    public CollectFirst deserialize(DataInput2 input, int available) throws IOException {
        CollectFirstSetting setting = CollectFirstSetting.valueOf(input.readUTF());
        boolean foundInServer = input.readBoolean();
        return new CollectFirst(setting, foundInServer);
    }
}
