import subprocess
from dataclasses import dataclass

@dataclass
class Attendee:
    line:str
    first_name: str
    last_name: str

def attendee_found(a):
    file = create_badge(a)
    show_badge(file)
    print_badge(file)

def create_badge(attendee, name_only=True):
    from PIL import Image, ImageDraw, ImageFont
    import textwrap

    name = "\n".join(
            textwrap.wrap(
                f"{attendee.first_name} {attendee.last_name}",
                width=12
            )
        )

    filename = f"{attendee.first_name}_{attendee.last_name}.png".lower()

    width, height = 600, 400
    
    white_color = (255, 255, 255)
    black_color = (0, 0, 0)
    
    name_size = 80
    name_y = 250
    name_y = height/2

    font_font = "JetBrainsMono-Regular.otf"
    font_name = ImageFont.truetype(font_font, name_size)
    
    img = Image.new('RGB', (width, height), color=white_color)
    draw = ImageDraw.Draw(img)
    
    draw.multiline_text((width/2, name_y), name, fill=black_color, anchor="mm", align="center", font=font_name)
    
    img.save(filename)

    return filename

def show_badge(file):
    subprocess.call(["chafa", file])

def print_badge(file):
    subprocess.call(["termux-share", "-d", file])

class Scanner:
    def __init__(self, csv_file):
        self.db = self.__parse(csv_file)

    def __parse(self, file):
        with open(file, newline='') as f:
            import csv
            reader = csv.DictReader(f)
            return [
                # Order number,Ticket number,First Name,Last Name,Email,Twitter,Company,Title,Featured,Ticket title,Ticket venue,Access code,Discount,Price,Currency,Number of tickets,Paid by (name),Paid by (email),Paid date (UTC),Checkin Date (UTC),Ticket Price Paid
                Attendee(str(x), x["First Name"], x["Last Name"]) 
                for x in reader
        ]
    
    def find_attendee(self, entry):
        if ':' in entry:
            attendee = self.get_from_qr(entry)
        else:
            attendee = self.match_from_db(entry)

        return attendee

    def get_from_qr(self, identifier):
        event_id, attendee_id = identifier.split(':')
        self.match_from_db(attendee_id)

    def match_from_db(self, needle):
        return list(filter(lambda x: needle.lower() in x.line.lower(), self.db))

if __name__ == "__main__":
    print("Welcome to XXX")

    s = Scanner("db.csv")

    go_on = True
    while go_on:
        search = input("\nscan attendee rsvp qr code or\nenter their name or\nor 'new' for manual attendee addition\nor 'exit' to quit out of this\n\n> ")
        if not search or len(search) == 0:
            continue

        a = s.find_attendee(search)
        if a and len(a) > 0 :
            print(f"Attendee(s) found: {len(a)}")
            [print(f"{x.first_name} {x.last_name}") for x in a]
            if len(a) == 1:
                attendee_found(a[0])
            else:
                print("\nMore than one attendee found, try again.")
        elif search and search.lower() == "exit":
            go_on = False
        elif search and search.lower() == "new":
            a = Attendee("",input("first name? "),input("last name? "))
            attendee_found(a)
        else:
            print("No one found.")
    
    print("\nThanks, good bye.")


