import os
import re

for root, dirs, files in os.walk("/Users/gusml/NTT Data/Mis Laboratorios Capacitación/microservicios-nttdata-solucion/tacocloud"):
    for file in files:
        if file == "pom.xml":
            path = os.path.join(root, file)
            with open(path, "r") as f:
                content = f.read()
            new_content = content
            new_content = new_content.replace("paren </", "parent</")
            new_content = new_content.replace("contrac </", "contract</")
            new_content = new_content.replace("boo </", "boot</")
            new_content = new_content.replace("projec </", "project</")
            new_content = new_content.replace("SNAPSHO </", "SNAPSHOT</")
            new_content = new_content.replace("ar </", "art</")
            new_content = new_content.replace("rabbi </", "rabbit</")
            
            # The issue was any closing tag with missing 't'
            # Let's just fix it manually if it fails
            if new_content != content:
                with open(path, "w") as f:
                    f.write(new_content)
