package WayofTime.bloodmagic.livingArmour;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import WayofTime.bloodmagic.api.livingArmour.LivingArmourHandler;
import WayofTime.bloodmagic.api.livingArmour.LivingArmourUpgrade;
import WayofTime.bloodmagic.api.livingArmour.StatTracker;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class LivingArmour
{
    public HashMap<String, StatTracker> trackerMap = new HashMap<String, StatTracker>();
    public HashMap<String, LivingArmourUpgrade> upgradeMap = new HashMap<String, LivingArmourUpgrade>();

    public int maxUpgradePoints = 100;
    public int totalUpgradePoints = 0;

    public Multimap<String, AttributeModifier> getAttributeModifiers()
    {
        HashMultimap<String, AttributeModifier> modifierMap = HashMultimap.<String, AttributeModifier>create();

        for (Entry<String, LivingArmourUpgrade> entry : upgradeMap.entrySet())
        {
            LivingArmourUpgrade upgrade = entry.getValue();
            if (upgrade == null)
            {
                continue;
            }
            modifierMap.putAll(upgrade.getAttributeModifiers());
        }

        return modifierMap;
    }

    public boolean upgradeArmour(EntityPlayer user, LivingArmourUpgrade upgrade)
    {
        String key = upgrade.getUniqueIdentifier();
        if (upgradeMap.containsKey(key))
        {
            //Check if this is a higher level than the previous upgrade
            int nextLevel = upgrade.getUpgradeLevel();
            int currentLevel = upgradeMap.get(key).getUpgradeLevel();

            if (nextLevel > currentLevel)
            {
                int upgradePointDifference = upgrade.getCostOfUpgrade() - upgradeMap.get(key).getCostOfUpgrade();
                if (upgradePointDifference >= 0 && totalUpgradePoints + upgradePointDifference <= maxUpgradePoints)
                {
                    upgradeMap.put(key, upgrade);
                    notifyPlayerOfUpgrade(user, upgrade);
                    return true;
                }
            }
        } else
        {
            int upgradePoints = upgrade.getCostOfUpgrade();
            if (totalUpgradePoints + upgradePoints <= maxUpgradePoints)
            {
                upgradeMap.put(key, upgrade);
                notifyPlayerOfUpgrade(user, upgrade);
                return true;
            }
        }

        return false;
    }

    public void notifyPlayerOfUpgrade(EntityPlayer user, LivingArmourUpgrade upgrade)
    {
        System.out.println("Upgraded!");
    }

    /**
     * Ticks the upgrades and stat trackers, passing in the world and player as
     * well as the LivingArmour
     * 
     * @param world
     * @param player
     */
    public void onTick(World world, EntityPlayer player)
    {
        for (Entry<String, LivingArmourUpgrade> entry : upgradeMap.entrySet())
        {
            LivingArmourUpgrade upgrade = entry.getValue();

            if (upgrade == null)
            {
                continue;
            }

            upgrade.onTick(world, player, this);
        }

        for (Entry<String, StatTracker> entry : trackerMap.entrySet())
        {
            StatTracker tracker = entry.getValue();

            if (tracker == null)
            {
                continue;
            }

            if (tracker.onTick(world, player, this))
            {
                List<LivingArmourUpgrade> upgradeList = tracker.getUpgrades();

                for (LivingArmourUpgrade upgrade : upgradeList) //TODO: make a getNextUpgrade?
                {
                    upgradeArmour(player, upgrade);
                }
            }
        }
    }

    public void readFromNBT(NBTTagCompound tag)
    {
        NBTTagList upgradeTags = tag.getTagList("upgrades", 10);
        if (upgradeTags != null)
        {
            for (int i = 0; i < upgradeTags.tagCount(); i++)
            {
                NBTTagCompound upgradeTag = upgradeTags.getCompoundTagAt(i);
                String key = upgradeTag.getString("key");
                int level = upgradeTag.getInteger("level");
                NBTTagCompound nbtTag = upgradeTag.getCompoundTag("upgrade");
                LivingArmourUpgrade upgrade = LivingArmourHandler.generateUpgradeFromKey(key, level, nbtTag);
                if (upgrade != null)
                {
                    upgradeMap.put(key, upgrade);
                    totalUpgradePoints += upgrade.getCostOfUpgrade();
                }
            }
        }

        for (Class<? extends StatTracker> clazz : LivingArmourHandler.trackers)
        {
            try
            {
                Constructor<?> ctor = clazz.getConstructor();
                Object obj = ctor.newInstance();
                if (!(obj instanceof StatTracker))
                {
                    // ?????
                }
                StatTracker tracker = (StatTracker) obj;
                String key = tracker.getUniqueIdentifier();
                NBTTagCompound trackerTag = tag.getCompoundTag(key);
                if (!trackerTag.hasNoTags())
                {
                    tracker.readFromNBT(trackerTag);
                }
                trackerMap.put(key, tracker);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void writeToNBT(NBTTagCompound tag, boolean forceWrite)
    {
        NBTTagList tags = new NBTTagList();

        for (Entry<String, LivingArmourUpgrade> entry : upgradeMap.entrySet())
        {
            NBTTagCompound upgradeTag = new NBTTagCompound();

            LivingArmourUpgrade upgrade = entry.getValue();
            NBTTagCompound nbtTag = new NBTTagCompound();
            upgrade.writeToNBT(nbtTag);

            upgradeTag.setString("key", upgrade.getUniqueIdentifier());
            upgradeTag.setInteger("level", upgrade.getUpgradeLevel());
            upgradeTag.setTag("upgrade", nbtTag);

            tags.appendTag(upgradeTag);
        }

        tag.setTag("upgrades", tags);

        for (Entry<String, StatTracker> entry : trackerMap.entrySet())
        {
            StatTracker tracker = entry.getValue();

            if (tracker == null)
            {
                continue;
            }

            String key = tracker.getUniqueIdentifier();

            if (forceWrite || tracker.isDirty())
            {
                NBTTagCompound trackerTag = new NBTTagCompound();
                tracker.writeToNBT(trackerTag);

                tag.setTag(key, trackerTag);

                tracker.resetDirty();
            }
        }
    }

    /**
     * Writes the LivingArmour to the NBTTag. This will only write the trackers
     * that are dirty.
     * 
     * @param tag
     */
    public void writeDirtyToNBT(NBTTagCompound tag)
    {
        writeToNBT(tag, false);
    }

    public void writeToNBT(NBTTagCompound tag)
    {
        writeToNBT(tag, true);
    }
}
