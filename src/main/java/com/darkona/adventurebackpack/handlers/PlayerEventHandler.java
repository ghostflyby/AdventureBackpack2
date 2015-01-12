package com.darkona.adventurebackpack.handlers;

import com.darkona.adventurebackpack.AdventureBackpack;
import com.darkona.adventurebackpack.common.BackpackProperty;
import com.darkona.adventurebackpack.common.ServerActions;
import com.darkona.adventurebackpack.config.ConfigHandler;
import com.darkona.adventurebackpack.entity.EntityFriendlySpider;
import com.darkona.adventurebackpack.entity.ai.EntityAIHorseFollowOwner;
import com.darkona.adventurebackpack.init.ModBlocks;
import com.darkona.adventurebackpack.init.ModItems;
import com.darkona.adventurebackpack.init.ModNetwork;
import com.darkona.adventurebackpack.item.IBackWearableItem;
import com.darkona.adventurebackpack.network.SyncPropertiesPacket;
import com.darkona.adventurebackpack.proxy.ServerProxy;
import com.darkona.adventurebackpack.reference.BackpackNames;
import com.darkona.adventurebackpack.util.LogHelper;
import com.darkona.adventurebackpack.util.Wearing;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemNameTag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;

/**
 * Created on 11/10/2014
 * Handle ALL the events!
 *
 * @author Darkona
 * @see com.darkona.adventurebackpack.common.ClientActions
 */
public class PlayerEventHandler
{

    @SubscribeEvent
    public void registerBackpackProperty(EntityEvent.EntityConstructing event)
    {
        if (event.entity instanceof EntityPlayer && BackpackProperty.get((EntityPlayer) event.entity) == null)
        {
            BackpackProperty.register((EntityPlayer) event.entity);
        }
    }

    /**
     * Used for the Piston Boots to give them their amazing powers.
     *
     * @param event
     */
    @SubscribeEvent
    public void onPlayerJump(LivingEvent.LivingJumpEvent event)
    {
        if (event.entity != null &&
                event.entityLiving instanceof EntityPlayer)
        {
            EntityPlayer player = (EntityPlayer) event.entity;


            if (Wearing.isWearingBoots(player) && player.onGround)
            {
                ServerActions.pistonBootsJump(player);
            }
        }
    }

    /**
     * Used by the Piston boots to lessen the fall damage. It's hacky, but I don't care.
     *
     * @param event
     */
    @SubscribeEvent
    public void onFall(LivingFallEvent event)
    {
        if (event.entity != null &&
                event.entityLiving instanceof EntityPlayer &&
                Wearing.isWearingBoots(((EntityPlayer) event.entityLiving)) &&
                event.distance < 8)
        {
            event.setCanceled(true);
        }

        if(!ConfigHandler.FIX_LEAD)return;
        if(event.entityLiving instanceof EntityCreature)
        {
            EntityCreature creature = (EntityCreature)event.entityLiving;
            if(creature.getLeashed() && creature.getLeashedToEntity() != null &&  creature.getLeashedToEntity() instanceof EntityPlayer)
            {
                EntityPlayer player = (EntityPlayer)creature.getLeashedToEntity();
                if(creature.motionY > -2.0f && player.motionY > -2.0f)
                {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public void playerIsBorn(EntityJoinWorldEvent event)
    {
        if (event.entity instanceof EntityPlayer)
        {
            EntityPlayer player = (EntityPlayer) event.entity;
            AdventureBackpack.proxy.joinPlayer(player);
        }
    }

    /**
     * @param event
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void playerDies(LivingDeathEvent event)
    {
        if (event.entity instanceof EntityPlayer)
        {
            EntityPlayer player = (EntityPlayer) event.entity;


            if (!player.worldObj.isRemote)
            {
                LogHelper.info("Player died");
                BackpackProperty props = BackpackProperty.get(player);
                if (props.getWearable()!=null)
                {
                    ItemStack wearable = BackpackProperty.get(player).getWearable();
                    LogHelper.info("Executing death protocol.");
                    ((IBackWearableItem)wearable.getItem()).onPlayerDeath(player.worldObj,player,wearable);
                }
                if(props.isForceCampFire())
                {
                    ChunkCoordinates lastCampFire = BackpackProperty.get(player).getCampFire();
                    if(lastCampFire != null)
                        //Set the forced spawn coordinates on the campfire. False, because the player must respawn at spawn point if there's no campfire.
                    player.setSpawnChunk(lastCampFire,false,player.dimension);
                }
                NBTTagCompound playerData = new NBTTagCompound();
                BackpackProperty.get(player).saveNBTData(playerData);
                ServerProxy.storePlayerProps(player.getCommandSenderName(), playerData);

                //LogHelper.info("Player " + player.getCommandSenderName() +  " just died, bedLocation is" + ((player.getBedLocation(player.dimension)!=null ) ? LogHelper.print3DCoords(player.getBedLocation(player.dimension)): "null"));
            }
        }

        event.setResult(Event.Result.ALLOW);
    }

    @SubscribeEvent
    public void playerCraftsBackpack(PlayerEvent.ItemCraftedEvent event)
    {
        if (event.crafting.getItem() == ModItems.adventureBackpack)
        {
            LogHelper.info("Player crafted a backpack, and that backpack's appearance is: " + event.crafting.getTagCompound().getString("colorName"));
            if (BackpackNames.getBackpackColorName(event.crafting).equals("Dragon"))
            {
                event.player.dropPlayerItemWithRandomChoice(new ItemStack(Blocks.dragon_egg, 1), false);
                event.player.playSound("mob.enderdragon.growl", 1.0f, 5.0f);
            }
        }
    }

    @SubscribeEvent
    public void rideSpider(EntityInteractEvent event)
    {
        EntityPlayer player = event.entityPlayer;
        if (!event.entityPlayer.worldObj.isRemote)
        {
            if (BackpackNames.getBackpackColorName(Wearing.getWearingBackpack(player)).equals("Spider"))
            {
                if (event.target instanceof EntitySpider)
                {
                    EntityFriendlySpider pet = new EntityFriendlySpider(event.target.worldObj);
                    pet.setLocationAndAngles(event.target.posX, event.target.posY, event.target.posZ, event.target.rotationYaw, event.target.rotationPitch);
                    event.target.setDead();
                    event.entityPlayer.worldObj.spawnEntityInWorld(pet);
                    event.entityPlayer.mountEntity(pet);
                }
            }
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public void ownHorse(EntityInteractEvent event)
    {
        if(!ConfigHandler.BACKPACK_ABILITIES)return;
        EntityPlayer player = event.entityPlayer;

        if (!event.entityPlayer.worldObj.isRemote)
        {

            ItemStack stack = player.getCurrentEquippedItem();
            if(stack!=null && stack.getItem()!=null && stack.getItem() instanceof ItemNameTag && stack.hasDisplayName())
            {
                if (event.target instanceof EntityHorse )
                {
                    EntityHorse horse = (EntityHorse)event.target;
                    if(horse.getCustomNameTag()==null ||horse.getCustomNameTag().equals("") && horse.isTame())
                    {
                        horse.setTamedBy(player);
                        horse.tasks.addTask(4, new EntityAIHorseFollowOwner(horse, 1.5d, 2.0f, 20.0f));

                        if (horse.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.followRange) != null)
                        {
                            horse.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.followRange).setBaseValue(100.0D);
                            LogHelper.info("the horse follow range is now: " + horse.getEntityAttribute(SharedMonsterAttributes.followRange).getBaseValue());
                        }
                    }
                }
            }
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent
    public void playerWokeUp(PlayerWakeUpEvent event)
    {
        if(event.entity.worldObj.isRemote)return;
        ChunkCoordinates bedLocation = event.entityPlayer.getBedLocation(event.entityPlayer.dimension);
        if(bedLocation != null && event.entityPlayer.worldObj.getBlock(bedLocation.posX,bedLocation.posY,bedLocation.posZ) == ModBlocks.blockSleepingBag)
        {
            //If the player wakes up in one of those super confortable SleepingBags (tm) (Patent Pending)
            BackpackProperty.get(event.entityPlayer).setForceCampFire(true);
            LogHelper.info("Player just woke up in a sleeping bag, forcing respawn in the last lighted campfire, if there's any");
        }else{
            //If it's a regular bed or whatever
            BackpackProperty.get(event.entityPlayer).setForceCampFire(false);
        }
    }

    @SubscribeEvent
    public void tickPlayer(TickEvent.PlayerTickEvent event)
    {
        if(event.player!=null && !event.player.isDead)
        {
            if(event.side.isServer())
            {
                NBTTagCompound props = new NBTTagCompound();
                BackpackProperty.get(event.player).saveNBTData(props);
                ModNetwork.net.sendTo(new SyncPropertiesPacket.Message(props), (EntityPlayerMP)event.player);
            }
            if(event.phase == TickEvent.Phase.END)
            {
                ItemStack backpack = BackpackProperty.get(event.player).getWearable();
                if(backpack != null && backpack.getItem() instanceof IBackWearableItem)
                {
                    ((IBackWearableItem) backpack.getItem()).onEquippedUpdate(event.player.worldObj, event.player, backpack);
                }
            }
        }
    }

}

