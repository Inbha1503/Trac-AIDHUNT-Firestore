#!/bin/bash
sed -i 's/val phoneRequester = remember { BringIntoViewRequester() }/val phoneRequester = remember { BringIntoViewRequester() }\n    val roleRequester = remember { BringIntoViewRequester() }/g' app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
