#define is_ouya
/*
**  Usage:
**      is_ouya()
**
**  Arguments:
**      None
**
**  Returns:
**      Boolean
**
**  Returns weter or not we are running the game on an actual OUYA console
** 
**  Manuel Etchegaray
**
*/

os_map = os_get_info();
if os_map != -1 {
    device = ds_map_find_value(os_map, "DEVICE");
    ds_map_destroy(os_map);
    if device == "cardhu" || device == "ouya_1_1" {
        return true;
    }
}

return false;


