package be.casperverswijvelt.tiles.shizuku;

import be.casperverswijvelt.tiles.shizuku.CommandResult;

interface IUserService {
    CommandResult executeCommand(String cmd) = 1;
    void destroy() = 16777114;
}
