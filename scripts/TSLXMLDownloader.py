#!/usr/bin/python

import urllib.request
import ssl
import argparse
import os
import shutil
from xml.dom import minidom

def parse_arguments():
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description='TSL XML Downloader')
    parser.add_argument('--tslFile', default='eu-lotl.xml', type=str, help='TSL file location')
    parser.add_argument('--tslTestFile', type=str, help='Test TSL file location')
    parser.add_argument('--countries', required=True, type=str, help='Countries, e.g., "EE;EE_T"')
    parser.add_argument('--isDevBuild', required=True, type=str, help='Indicates if build is dev version, e.g., "True"')
    return parser.parse_args()

def load_xml(tsl_file_location, tsl_test_file_location, is_test_file):
    """Load the appropriate XML file based on country and test file presence."""
    if is_test_file:
        if tsl_test_file_location is None or tsl_test_file_location == tsl_file_location:
            return None
        return minidom.parse(tsl_test_file_location)
    
    if os.path.isfile(tsl_file_location):
        return minidom.parse(tsl_file_location)
    return minidom.parse(tsl_test_file_location)

def download_tsl(tsl_url, country_code, is_dev_build, processed_urls):
    """Download TSL data from the given URL and save it."""
    # Check if the URL has already been processed
    if tsl_url in processed_urls:
        print(f"Skipping already processed URL: {tsl_url}")
        return
    
    print(f"{country_code} TSL-URL: {tsl_url}")
    
    # Get data from TSL-URL
    response = urllib.request.urlopen(tsl_url).read()
    
    # Decode the response from bytes to string (assuming UTF-8 encoding)
    response_str = response.decode('utf-8')
    
    # Get the directory of the current script
    script_directory = os.path.dirname(os.path.abspath(__file__))
    
    # Define the TSL subfolder path
    tsl_folder_path = os.path.join(script_directory, "TSL")
    
    # Create the TSL subfolder if it doesn't exist
    if not os.path.exists(tsl_folder_path):
        os.makedirs(tsl_folder_path)
    
    # Save data to a file in the TSL subfolder
    filename = f"{country_code}.xml"
    file_path = os.path.join(tsl_folder_path, filename)
    
    with open(file_path, 'w', encoding='utf-8') as file:
        file.write(response_str)
    
    # Move the file to assets/tslFiles if needed (this can still be kept for your build logic)
    destination = os.path.join(tsl_folder_path, filename)
    shutil.move(file_path, destination)
    
    # Mark this URL as processed
    processed_urls.add(tsl_url)


def process_country(input_country, xmldoc, is_dev_build, processed_urls):
    """Process XML data for a specific country and download TSL if applicable."""
    pointers_to_other_tsl = xmldoc.getElementsByTagName('PointersToOtherTSL')
    
    for pointer in pointers_to_other_tsl:
        other_tsl_pointers = pointer.getElementsByTagName('OtherTSLPointer')
        
        for pointer_data in other_tsl_pointers:
            tsl_locations = pointer_data.getElementsByTagName('TSLLocation')
            additional_info = pointer_data.getElementsByTagName('AdditionalInformation')
            
            for info in additional_info:
                other_info = info.getElementsByTagName('OtherInformation')
                
                for other_data in other_info:
                    scheme_territory = other_data.getElementsByTagName('SchemeTerritory')
                    mime_type = other_data.getElementsByTagName('ns3:MimeType') or other_data.getElementsByTagName('ns4:MimeType')
                    test_mime_type = other_data.getElementsByTagName('tslx:MimeType')
                    
                    for territory in scheme_territory:
                        pass
                    
                    # Process MimeType based on country
                    if "_T" in input_country:
                        for test_type in test_mime_type:
                            if territory.firstChild.nodeValue == input_country and test_type.firstChild.nodeValue == 'application/vnd.etsi.tsl+xml':
                                tsl_url = pointer_data.getElementsByTagName('TSLLocation')[0].firstChild.nodeValue  # Assign tsl_url here
                                download_tsl(tsl_url, territory.firstChild.nodeValue, is_dev_build, processed_urls)
                                break
                    else:
                        for mime in mime_type:
                            if territory.firstChild.nodeValue == input_country and mime.firstChild.nodeValue == 'application/vnd.etsi.tsl+xml':
                                tsl_url = pointer_data.getElementsByTagName('TSLLocation')[0].firstChild.nodeValue  # Assign tsl_url here
                                download_tsl(tsl_url, territory.firstChild.nodeValue, is_dev_build, processed_urls)
                                break

def main():
    """Main function to execute the TSL downloader."""
    # Parse arguments
    args = parse_arguments()

    input_countries = args.countries.split(';')
    is_dev_build = args.isDevBuild.lower() == "true"
    
    # Set to track processed TSL URLs
    processed_urls = set()

    for input_country in input_countries:
        xmldoc = load_xml(args.tslFile, args.tslTestFile, "_T" in input_country)

        if xmldoc is None:
            continue
        
        # Process country-specific data
        process_country(input_country, xmldoc, is_dev_build, processed_urls)
    
    # Validate country and mime type
    if len(input_country) == 2 and not any("_T" in input_country for _ in input_countries):
        raise Exception('TSL might have been updated. Check if the MimeType tag name has changed (nsx:MimeType).')


if __name__ == "__main__":
    main()