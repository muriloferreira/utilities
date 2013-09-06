function minutos2Horas(tMinutos) {
	var horas;
	var minutos;
	var negativo = false;
	tMinutos = parseInt(tMinutos);

	if(tMinutos < 0) {
		tMinutos = tMinutos * -1;
		negativo = true;
	}
	horas = parseInt((tMinutos / 60));
	minutos = (tMinutos % 60);
	if(horas < 10) {
		horas = "0"+horas;
	}
	if(minutos < 10) {
		minutos = "0"+minutos;
	}
	if(horas == 0) {
		horas = "00";
	}
	if(minutos == 0) {
		minutos = "00";
	}
	tHoras = horas + ":" + minutos;
	return (negativo?"- ":"") + tHoras;
}


// how to use example
<script type="text/javascript">document.write(minutos2Horas("327") + " | 327  minuto(s)");</script>
