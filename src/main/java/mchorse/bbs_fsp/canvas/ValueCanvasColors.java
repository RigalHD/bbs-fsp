package mchorse.bbs_fsp.canvas;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ValueCanvasColors extends BaseValue
{
    private static final int LIMIT = 12;
    private final List<Integer> colors = new ArrayList<>();

    public ValueCanvasColors(String id)
    {
        super(id);
    }

    public List<Integer> getColors()
    {
        return Collections.unmodifiableList(this.colors);
    }

    public void remember(int color)
    {
        if (!this.colors.isEmpty() && this.colors.get(0) == color)
        {
            return;
        }

        this.colors.remove(Integer.valueOf(color));
        this.colors.add(0, color);

        if (this.colors.size() > LIMIT)
        {
            this.colors.remove(LIMIT);
        }
    }

    @Override
    public BaseType toData()
    {
        ListType data = new ListType();

        for (int color : this.colors)
        {
            data.addInt(color);
        }

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.colors.clear();

        if (data != null && data.isList())
        {
            ListType list = data.asList();

            for (int i = 0; i < Math.min(list.size(), LIMIT); i++)
            {
                int color = list.getInt(i);

                if (!this.colors.contains(color))
                {
                    this.colors.add(color);
                }
            }
        }
    }
}
