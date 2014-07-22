package com.github.marenwynn.waypoints.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import com.github.marenwynn.waypoints.SelectionManager;
import com.github.marenwynn.waypoints.Util;
import com.github.marenwynn.waypoints.data.DataManager;
import com.github.marenwynn.waypoints.data.Msg;
import com.github.marenwynn.waypoints.data.Waypoint;

public class WPDescCmd implements PluginCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DataManager dm = DataManager.getManager();
        Waypoint wp = SelectionManager.getManager().getSelectedWaypoint(sender);

        if (wp == null) {
            Msg.WP_NOT_SELECTED_ERROR.sendTo(sender);
            Msg.WP_NOT_SELECTED_ERROR_USAGE.sendTo(sender);
            return true;
        }

        if (args.length < 2) {
            wp.setDescription("");
            dm.saveWaypoints();
            Msg.WP_DESC_CLEARED.sendTo(sender, wp.getName());
            return true;
        }

        String desc = Util.buildString(args, 1, ' ');

        if (desc.length() > dm.WP_DESC_MAX_LENGTH) {
            Msg.MAX_LENGTH_EXCEEDED.sendTo(sender, dm.WP_DESC_MAX_LENGTH);
            return true;
        }

        wp.setDescription(desc);
        dm.saveWaypoint(sender, wp);
        Msg.WP_DESC_UPDATED.sendTo(sender, wp.getName());
        return true;
    }

    @Override
    public boolean isConsoleExecutable() {
        return true;
    }

    @Override
    public boolean hasRequiredPerm(CommandSender sender) {
        return sender.hasPermission("wp.desc");
    }

}
