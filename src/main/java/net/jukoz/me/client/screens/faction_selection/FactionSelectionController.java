package net.jukoz.me.client.screens.faction_selection;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jukoz.me.network.packets.C2S.*;
import net.jukoz.me.resources.datas.Disposition;
import net.jukoz.me.resources.datas.FactionType;
import net.jukoz.me.resources.datas.factions.Faction;
import net.jukoz.me.resources.datas.factions.FactionLookup;
import net.jukoz.me.resources.datas.npcs.data.NpcGearData;
import net.jukoz.me.resources.datas.factions.data.SpawnData;
import net.jukoz.me.resources.datas.factions.data.SpawnDataHandler;
import net.jukoz.me.resources.datas.races.Race;
import net.jukoz.me.utils.LoggerUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector2d;
import org.joml.Vector2i;

import java.util.*;

public class FactionSelectionController {
    private Map<Disposition, List<Faction>> factions = null;
    private List<SpawnData> spawns;
    private List<Race> races = new ArrayList<>();

    private int currentDispositionIndex = 0;
    private int currentFactionIndex = 0;
    private int currentSubFactionIndex = 0;
    private int currentRaceIndex = 0;
    private int currentSpawnIndex = 0;

    private AbstractClientPlayerEntity player;
    private FactionSelectionScreen screen;
    public boolean mapFocusToggle = true;
    private List<Disposition> dispositionsWithContent = new ArrayList<>();
    private float currentDelay;

    public FactionSelectionController(FactionSelectionScreen screen, AbstractClientPlayerEntity player, float delay) {
        this.player = player;
        this.screen = screen;
        this.currentDelay = delay;

        factions = new HashMap<>();
        addFactionsByDisposition(Disposition.GOOD);
        addFactionsByDisposition(Disposition.NEUTRAL);
        addFactionsByDisposition(Disposition.EVIL);

        // Set default disposition if none selected
        if (dispositionsWithContent.isEmpty()) {
            LoggerUtil.logError("FactionSelectionController::No factions available at all!");
            throw new RuntimeException("No factions loaded");
        }

        currentDispositionIndex = 0; // Start with first valid disposition
        ensureValidFactionAndSubfaction();
        processSpawnList(0);
        processRace();

        if (currentDelay == 0) {
            screen.enableConfirm();
        }
    }

    private void addFactionsByDisposition(Disposition disposition) {
        List<Faction> found = new ArrayList<>(
                FactionLookup.getFactionsByDisposition(player.getWorld(), disposition).values());
        found.sort(Comparator.comparingInt(Faction::getFactionSelectionOrderIndex));
        factions.put(disposition, found);
        if (!found.isEmpty()) {
            dispositionsWithContent.add(disposition);
        }
    }

    /** Ensures we have a valid faction and subfaction selected */
    private void ensureValidFactionAndSubfaction() {
        Disposition disp = getCurrentDisposition();
        List<Faction> factionList = factions.get(disp);
        if (factionList == null || factionList.isEmpty()) {
            // Find first disposition with content
            currentDispositionIndex = 0;
            disp = dispositionsWithContent.get(0);
            factionList = factions.get(disp);
        }

        // Clamp faction index
        currentFactionIndex = Math.max(0, Math.min(currentFactionIndex, factionList.size() - 1));

        // Auto-select first subfaction if available
        Faction faction = factionList.get(currentFactionIndex);
        List<Identifier> subIds = faction.getSubFactions();
        if (subIds != null && !subIds.isEmpty()) {
            currentSubFactionIndex = Math.max(0, Math.min(currentSubFactionIndex, subIds.size() - 1));
        } else {
            currentSubFactionIndex = 0;
        }
    }

    private void processSpawnList(int index) {
        Faction current = getCurrentlySelectedFaction();
        if (current == null) {
            spawns = Collections.emptyList();
            return;
        }

        SpawnDataHandler handler = current.getSpawnData();
        spawns = (handler != null) ? handler.getSpawnList() : Collections.emptyList();

        currentSpawnIndex = spawns.isEmpty() ? 0 : Math.max(0, Math.min(index, spawns.size() - 1));
        setSpawnIndex(currentSpawnIndex);
    }

    private void processRace() {
        Faction current = getCurrentlySelectedFaction();
        races = (current != null) ? current.getRaces(player.getWorld()) : Collections.emptyList();
        currentRaceIndex = races.isEmpty() ? 0 : Math.min(currentRaceIndex, races.size() - 1);

        screen.updateEquipment();
        screen.reassignTexts(getRaceListText(), getCurrentFactionDescriptions());
    }

    public void randomizeSpawn(int tentativeLeft) {
        if (!haveSpawns()) return;

        Random random = new Random();
        for (int i = 0; i < tentativeLeft; i++) {
            int newIndex = random.nextInt(spawns.size());
            if (newIndex != currentSpawnIndex) {
                processSpawnList(newIndex);
                return;
            }
        }
        processSpawnList(random.nextInt(spawns.size()));
    }

    public int randomizeFaction(int tentativeLeft) {
        Random random = new Random();

        currentDispositionIndex = random.nextInt(dispositionsWithContent.size());
        Disposition disp = dispositionsWithContent.get(currentDispositionIndex);

        List<Faction> factionList = factions.get(disp);
        if (factionList == null || factionList.isEmpty()) {
            if (tentativeLeft > 0) return randomizeFaction(tentativeLeft - 1);
            factionList = factions.get(dispositionsWithContent.get(0)); // fallback
        }

        currentFactionIndex = random.nextInt(factionList.size());
        Faction faction = factionList.get(currentFactionIndex);

        List<Identifier> subfactions = faction.getSubFactions();
        if (subfactions != null && !subfactions.isEmpty()) {
            currentSubFactionIndex = random.nextInt(subfactions.size());
        } else {
            currentSubFactionIndex = 0;
        }

        currentRaceIndex = 0;
        processFaction();
        return 0;
    }

    public void dispositionUpdate(boolean next) {
        if (dispositionsWithContent.size() <= 1) return;

        currentDispositionIndex += next ? 1 : -1;
        if (currentDispositionIndex >= dispositionsWithContent.size()) currentDispositionIndex = 0;
        if (currentDispositionIndex < 0) currentDispositionIndex = dispositionsWithContent.size() - 1;

        currentFactionIndex = 0;
        currentSubFactionIndex = 0;
        currentRaceIndex = 0;

        ensureValidFactionAndSubfaction();
        processFaction();
        screen.updateConfirmButton();
    }

    public void factionUpdate(boolean next) {
        List<Faction> list = factions.get(getCurrentDisposition());
        if (list.size() <= 1) return;

        currentFactionIndex += next ? 1 : -1;
        if (currentFactionIndex >= list.size()) currentFactionIndex = 0;
        if (currentFactionIndex < 0) currentFactionIndex = list.size() - 1;

        currentSubFactionIndex = 0; // Reset subfaction
        currentRaceIndex = 0;

        ensureValidFactionAndSubfaction();
        processFaction();
        screen.updateConfirmButton();
    }

    public void subfactionUpdate(boolean next) {
        Faction faction = getCurrentFaction();
        List<Identifier> subfactions = faction.getSubFactions();
        if (subfactions == null || subfactions.size() <= 1) return;

        currentSubFactionIndex += next ? 1 : -1;
        if (currentSubFactionIndex >= subfactions.size()) currentSubFactionIndex = 0;
        if (currentSubFactionIndex < 0) currentSubFactionIndex = subfactions.size() - 1;

        processSubfaction();
        screen.updateConfirmButton();
    }

    private void processFaction() {
        ensureValidFactionAndSubfaction();
        processSubfaction();
        processSpawnList(0);
        processRace();
    }

    private void processSubfaction() {
        processSpawnList(0);
        processRace();
    }

    public void spawnIndexUpdate(boolean next) {
        if (!haveSpawns() || spawns.size() <= 1) return;

        currentSpawnIndex += next ? 1 : -1;
        if (currentSpawnIndex >= spawns.size()) currentSpawnIndex = 0;
        if (currentSpawnIndex < 0) currentSpawnIndex = spawns.size() - 1;

        setSpawnIndex(currentSpawnIndex);
        screen.updateConfirmButton();
    }

    public void raceIndexUpdate(boolean next) {
        if (races.size() <= 1) return;

        currentRaceIndex += next ? 1 : -1;
        if (currentRaceIndex >= races.size()) currentRaceIndex = 0;
        if (currentRaceIndex < 0) currentRaceIndex = races.size() - 1;

        screen.updateEquipment();
        screen.updateConfirmButton();
    }

    // Getters
    public Disposition getCurrentDisposition() {
        return dispositionsWithContent.get(
                Math.max(0, Math.min(currentDispositionIndex, dispositionsWithContent.size() - 1)));
    }

    public Faction getCurrentFaction() {
        List<Faction> list = factions.get(getCurrentDisposition());
        if (list == null || list.isEmpty()) return null;
        return list.get(Math.max(0, Math.min(currentFactionIndex, list.size() - 1)));
    }

    public Faction getCurrentSubfaction() {
        Faction faction = getCurrentFaction();
        if (faction == null) return null;
        List<Identifier> subs = faction.getSubFactions();
        if (subs == null || subs.isEmpty()) return null;
        int idx = Math.max(0, Math.min(currentSubFactionIndex, subs.size() - 1));
        return faction.getSubfactionById(player.getWorld(), subs.get(idx));
    }

    public Faction getCurrentlySelectedFaction() {
        Faction sub = getCurrentSubfaction();
        return (sub != null) ? sub : getCurrentFaction();
    }

    public boolean haveSubfaction() {
        return getCurrentSubfaction() != null;
    }

    public boolean haveManyRaces() {
        return races != null && races.size() > 1;
    }

    public boolean haveManySpawns() {
        return haveSpawns() && spawns.size() > 1;
    }

    public boolean haveSpawns() {
        return spawns != null && !spawns.isEmpty();
    }

    public Race getCurrentRace() {
        return races.isEmpty() ? null : races.get(currentRaceIndex);
    }

    public String getCurrentRaceKey() {
        Race race = getCurrentRace();
        return race != null ? race.getTranslatableKey() : "screen.me.race.none";
    }

    public String getCurrentSpawnKey() {
        if (!haveSpawns()) return "spawn.me.none";
        Identifier id = spawns.get(currentSpawnIndex).getIdentifier();
        return SpawnDataHandler.getTranslatableKey(id);
    }

    public int getCurrentDispositionFactionCount() {
        List<Faction> list = factions.get(getCurrentDisposition());
        return list != null ? list.size() : 0;
    }

    public NpcGearData getCurrentPreview(World world) {
        Faction faction = getCurrentlySelectedFaction();
        if (faction == null) return null;
        races = faction.getRaces(world);
        return faction.getPreviewGear(world, races.get(currentRaceIndex));
    }

    public void setSpawnIndex(int index) {
        if (!haveSpawns()) {
            currentSpawnIndex = 0;
            return;
        }
        currentSpawnIndex = Math.max(0, Math.min(index, spawns.size() - 1));

        if (screen.mapWidget != null) {
            screen.mapWidget.updateSelectedSpawn(currentSpawnIndex);
            if (mapFocusToggle) {
                SpawnData spawn = spawns.get(currentSpawnIndex);
                BlockPos pos = spawn.getBlockPos();
                screen.mapWidget.moveTo(new Vector2i(pos.getX(), pos.getZ()), new Vector2d(3.5, 45.0));
            }
        }
    }

    public int getCurrentSpawnIndex() {
        return currentSpawnIndex;
    }

    public List<Text> getCurrentFactionDescriptions() {
        Faction f = getCurrentlySelectedFaction();
        return f != null ? f.getDescription() : Collections.emptyList();
    }

    public List<Text> getRaceListText() {
        Faction f = getCurrentlySelectedFaction();
        return f != null ? List.of(f.getRaceListText(player.getWorld())) : Collections.emptyList();
    }

    /** Now properly checks subfaction requirement */
    public boolean canConfirm() {
        if (currentDelay > 0) return false;
        if (getCurrentlySelectedFaction() == null) return false;
        if (!haveSpawns()) return false;

        // Critical: Require subfaction selection if the main faction has subfactions
        Faction main = getCurrentFaction();
        if (main != null && main.getSubFactions() != null && !main.getSubFactions().isEmpty()) {
            if (getCurrentSubfaction() == null) return false;
        }

        return true;
    }

    public float getDelayRounded() {
        return (Math.round(this.currentDelay * 10f) / 10f);
    }

    public void reduceDelay(float delta) {
        if (currentDelay > 0) {
            currentDelay = Math.max(0, currentDelay - delta);
            if (currentDelay == 0) {
                screen.enableConfirm();
            }
        }
    }

    public void confirmSpawnSelection(AbstractClientPlayerEntity player) {
        Faction finalFaction = getCurrentlySelectedFaction();

        if (finalFaction == null) {
            player.sendMessage(Text.translatable("screen.me.error.no_faction").formatted(net.minecraft.util.Formatting.RED), true);
            LoggerUtil.logError("confirmSpawnSelection: No faction selected!");
            return;
        }

        if (!haveSpawns()) {
            player.sendMessage(Text.translatable("screen.me.error.no_spawn").formatted(net.minecraft.util.Formatting.RED), true);
            return;
        }

        // Extra safety for subfaction
        Faction main = getCurrentFaction();
        if (main != null) {
            List<Identifier> subfactions = main.getSubFactions();
            if (subfactions != null && !subfactions.isEmpty() && getCurrentSubfaction() == null) {
                player.sendMessage(Text.translatable("screen.me.error.no_subfaction").formatted(net.minecraft.util.Formatting.RED), true);
                return;
            }
        }

        SpawnData spawn = spawns.get(currentSpawnIndex);
        Vec3d pos = spawn.getCoordinates();

        if (spawn.isDynamic()) {
            ClientPlayNetworking.send(new PacketTeleportToDynamicCoordinate(pos.getX(), pos.getZ(), true));
        } else {
            ClientPlayNetworking.send(new PacketTeleportToCustomCoordinate(pos.getX(), pos.getY(), pos.getZ(), true));
        }

        Race race = getCurrentRace();
        if (race != null) {
            ClientPlayNetworking.send(new PacketSetRace(race.getId().toString()));
        }

        ClientPlayNetworking.send(new PacketSetAffiliation(
                getCurrentDisposition().name(),
                finalFaction.getId().toString(),
                spawn.getIdentifier().toString()
        ));

        if (player != null) {
            BlockPos overworldPos = player.getBlockPos();
            ClientPlayNetworking.send(new PacketSetSpawnData(overworldPos.getX(), overworldPos.getY(), overworldPos.getZ()));
        }

        screen.close();
    }

    public Map<Disposition, List<Faction>> getFactions() {
        return factions;
    }

    public SpawnDataHandler getCurrentSpawnDataHandler(){
        Faction faction = getCurrentlySelectedFaction();
        return (faction != null) ? faction.getSpawnData() : null;
    }

    public HashMap<Identifier, Text> getSearchBarPool(World world) {
        HashMap<Identifier, Text> pool = new HashMap<>();
        for(List<Faction> factionsByDisposition : factions.values()){
            for(Faction faction : factionsByDisposition){
                pool.put(faction.getId(), faction.tryGetShortName());
                if(faction.getFactionType() == FactionType.FACTION && faction.getSubFactions() != null){
                    for(Identifier identifier : faction.getSubFactions()){
                        Faction subfaction = faction.getSubfactionById(world, identifier);
                        pool.put(subfaction.getId(), subfaction.tryGetShortName());
                    }
                }
            }
        }
        return pool;
    }

    public void toggleMapFocus() {
        mapFocusToggle = !mapFocusToggle;
        if(!mapFocusToggle){
            screen.mapWidget.clearFocus();
        }
    }

    public void setFactionId(Identifier id) {
        for(Disposition disp : factions.keySet()){
            for(Faction fac : factions.get(disp)){
                boolean foundFaction = false;
                int subfactionIndex = -1;
                if(fac.getId() == id){
                    foundFaction = true;
                } else {
                    List<Identifier> subfactions = fac.getSubFactions();
                    if(subfactions != null && !subfactions.isEmpty()){
                        if(subfactions.contains(id)){
                            subfactionIndex = fac.getSubFactions().indexOf(id);
                            foundFaction = true;
                        }
                    }
                }
                if(foundFaction){
                    currentDispositionIndex = dispositionsWithContent.indexOf(disp);
                    currentFactionIndex = factions.get(disp).stream().toList().indexOf(fac);
                    if(subfactionIndex >= 0)
                        currentSubFactionIndex = subfactionIndex;
                    processFaction();
                    return;
                }
            }
        }
    }
}
